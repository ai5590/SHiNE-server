package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.websocket.api.Session;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.*;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import server.ws.WsConnectionUtils;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.entities.ActiveSessionEntry;
import shine.db.entities.SolanaUserEntry;
import shine.geo.ClientInfoService;
import shine.geo.GeoLookupService;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.security.SecureRandom;
import java.util.Base64;

public class Net_CreateAuthSession__Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_CreateAuthSession__Handler.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final long ALLOWED_SKEW_MS = 30_000L;

    public static boolean verifyAuthorificatedSignature(
            SolanaUserEntry user,
            String authNonce,
            long timeMs,
            String signatureB64
    ) throws IllegalArgumentException {

        String pubKeyB64 = user.getDeviceKey();
        byte[] publicKey32 = Ed25519Util.keyFromBase64(pubKeyB64);
        byte[] signature64 = Base64.getDecoder().decode(signatureB64);

        String preimageStr = "AUTHORIFICATED:" + timeMs + authNonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);

        return Ed25519Util.verify(preimage, signature64, publicKey32);
    }

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {
        Net_CreateAuthSession_Request req = (Net_CreateAuthSession_Request) baseReq;

        if (ctx == null
                || ctx.getSolanaUser() == null
                || ctx.getAuthNonce() == null
                || ctx.getAuthenticationStatus() != ConnectionContext.AUTH_STATUS_AUTH_IN_PROGRESS) {

            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "NO_STEP1_CONTEXT",
                    "Шаг 1 авторизации не был корректно выполнен для данного соединения"
            );
            WsConnectionUtils.closeConnection(ctx, 4001, "Auth failed: no step1 context or bad auth state");
            return err;
        }

        SolanaUserEntry user = ctx.getSolanaUser();
        String login = user.getLogin();
        if (login == null || login.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "NO_LOGIN",
                    "Для пользователя не задан login в БД"
            );
            WsConnectionUtils.closeConnection(ctx, 4001, "Auth failed: no login");
            return err;
        }

        String storagePwd = req.getStoragePwd();
        if (storagePwd == null || storagePwd.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_STORAGE_PWD",
                    "Пустой storagePwd"
            );
            WsConnectionUtils.closeConnection(ctx, 4001, "Auth failed: empty storagePwd");
            return err;
        }

        String signatureB64 = req.getSignatureB64();
        if (signatureB64 == null || signatureB64.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_SIGNATURE",
                    "Пустая цифровая подпись"
            );
            WsConnectionUtils.closeConnection(ctx, 4001, "Auth failed: empty signature");
            return err;
        }

        long timeMs = req.getTimeMs();
        long nowMs = System.currentTimeMillis();

        long diff = Math.abs(nowMs - timeMs);
        if (diff > ALLOWED_SKEW_MS) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "TIME_SKEW",
                    "Время клиента отличается от сервера более чем на 30 секунд"
            );
            WsConnectionUtils.closeConnection(ctx, 4001, "Auth failed: time skew");
            return err;
        }

        String clientInfoFromClient = req.getClientInfo();
        if (clientInfoFromClient != null && clientInfoFromClient.length() > 50) {
            clientInfoFromClient = clientInfoFromClient.substring(0, 50);
        }

        String pubKeyB64 = user.getDeviceKey();
        if (pubKeyB64 == null || pubKeyB64.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "NO_PUBKEY1",
                    "Отсутствует публичный ключ pubkey1 для пользователя"
            );
            WsConnectionUtils.closeConnection(ctx, 4001, "Auth failed: no pubkey");
            return err;
        }

        String authNonce = ctx.getAuthNonce();

        boolean sigOk;
        try {
            sigOk = verifyAuthorificatedSignature(user, authNonce, timeMs, signatureB64);
        } catch (IllegalArgumentException ex) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_BASE64",
                    "Некорректный формат Base64 для ключа или подписи"
            );
            WsConnectionUtils.closeConnection(ctx, 4001, "Auth failed: bad base64");
            return err;
        }

        if (!sigOk) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "BAD_SIGNATURE",
                    "Подпись не прошла проверку"
            );
            WsConnectionUtils.closeConnection(ctx, 4001, "Auth failed: bad signature");
            return err;
        }

        // --- Генерируем настоящий секрет сессии (sessionPwd) и sessionId ---
        String newSessionPwd = generateRandomSecret();
        String sessionId = generateRandomSessionId();
        long now = System.currentTimeMillis();

        // --- Сбор данных о клиенте (IP, UA, язык) ---
        Session wsSession = ctx.getWsSession();
        String clientInfoFromRequest = ClientInfoService.buildClientInfoString(wsSession);
        String userLanguage = ClientInfoService.extractPreferredLanguageTag(wsSession);

        String clientIp = null;
        if (wsSession != null) {
            clientIp = ClientInfoService.extractClientIp(wsSession);

            if (clientIp != null && !clientIp.isBlank()) {
                try {
                    GeoLookupService.resolveCountryCityOrIpWithCache(clientIp);
                } catch (Exception e) {
                    log.debug("Geo lookup failed for ip={}", clientIp, e);
                }
            }
        }
        if (clientIp == null) clientIp = "";

        // --- создаём запись ActiveSession и сохраняем в БД ---
        ActiveSessionsDAO dao = ActiveSessionsDAO.getInstance();
        ActiveSessionEntry activeSessionEntry;

        try {
            activeSessionEntry = new ActiveSessionEntry(
                    sessionId,
                    login,
                    newSessionPwd,
                    storagePwd,
                    now,
                    now,
                    null,                    // pushEndpoint
                    null,                    // pushP256dhKey
                    null,                    // pushAuthKey
                    clientIp,
                    clientInfoFromClient,
                    clientInfoFromRequest,
                    userLanguage
            );

            dao.insert(activeSessionEntry);
        } catch (SQLException e) {
            log.error("Ошибка БД при создании новой сессии для login={}", login, e);
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR_SESSION_CREATE",
                    "Ошибка БД при создании сессии"
            );
            WsConnectionUtils.closeConnection(ctx, 4001, "Auth failed: db error");
            return err;
        }

        // --- обновляем контекст ---
        ctx.setActiveSession(activeSessionEntry);
        ctx.setSessionId(sessionId);
        ctx.setSessionPwd(newSessionPwd);   // теперь в контексте хранится секрет сессии
        ctx.setAuthNonce(null);            // одноразовый nonce больше не нужен
        ctx.setAuthenticationStatus(ConnectionContext.AUTH_STATUS_USER);

        ActiveConnectionsRegistry.getInstance().register(ctx);

        // --- формируем ответ ---
        Net_CreateAuthSession_Response resp = new Net_CreateAuthSession_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setSessionId(sessionId);
        resp.setSessionPwd(newSessionPwd);
        return resp;
    }

    /**
     * Генерация случайного sessionId: base64-строка от 32 байт.
     */
    private String generateRandomSessionId() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * Генерация случайного секрета (sessionPwd): base64-строка от 32 байт.
     */
    private String generateRandomSecret() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}