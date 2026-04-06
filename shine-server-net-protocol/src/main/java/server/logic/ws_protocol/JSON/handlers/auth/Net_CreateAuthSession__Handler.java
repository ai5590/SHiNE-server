package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.Base64Ws;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_CreateAuthSession_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_CreateAuthSession_Response;
import server.logic.ws_protocol.JSON.utils.AuthKeyUtils;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import server.ws.WsConnectionUtils;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.ActiveSessionEntry;
import shine.db.entities.SolanaUserEntry;
import shine.geo.ClientInfoService;
import shine.geo.GeoLookupService;
import utils.crypto.Ed25519Util;

import org.eclipse.jetty.websocket.api.Session;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;

/**
 * CreateAuthSession (v2) — шаг 2 создания новой сессии (ТОЛЬКО deviceKey).
 *
 * Логика авторизации (v2):
 *  - Создание сессии: AuthChallenge(login) -> authNonce -> CreateAuthSession(...)
 *  - Клиент генерирует sessionKey, хранит приватный ключ у себя,
 *    отправляет на сервер sessionKey целиком одной строкой.
 *  - Сервер сохраняет sessionKey в active_sessions.session_key как есть.
 *
 * Подпись deviceKey (Ed25519) проверяется над строкой (UTF-8):
 *   AUTH_CREATE_SESSION:{login}:{sessionKey}:{storagePwd}:{timeMs}:{authNonce}
 *
 * На выходе:
 *  - создаётся запись active_sessions
 *  - ctx становится AUTH_STATUS_USER (вход выполнен как "текущая сессия")
 *  - ответ: sessionId
 */
public class Net_CreateAuthSession__Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_CreateAuthSession__Handler.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CLOSE_AFTER_ERROR_DELAY_MS = 75L;

    public static final long ALLOWED_SKEW_MS = 30_000L;

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
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: no step1 context or bad auth state");
            return err;
        }

        SolanaUserEntry userFromContext = ctx.getSolanaUser();
        String loginFromContext = userFromContext.getLogin();
        String loginFromReq = req.getLogin();
        if (loginFromReq == null || loginFromReq.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_LOGIN",
                    "Пустой login"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: empty login");
            return err;
        }
        loginFromReq = loginFromReq.trim();
        if (!loginFromReq.equalsIgnoreCase(loginFromContext)) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "LOGIN_MISMATCH",
                    "login не соответствует контексту AuthChallenge"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: login mismatch");
            return err;
        }

        SolanaUserEntry user;
        try {
            user = SolanaUsersDAO.getInstance().getByLogin(loginFromContext);
        } catch (SQLException e) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR_USER_LOOKUP",
                    "Ошибка БД при получении пользователя"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: db user lookup");
            return err;
        }
        if (user == null) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "USER_NOT_FOUND",
                    "Пользователь не найден"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: user not found");
            return err;
        }

        String canonicalLogin = user.getLogin();
        if (canonicalLogin == null || canonicalLogin.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "NO_LOGIN",
                    "Для пользователя не задан login в БД"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: no login");
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
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: empty storagePwd");
            return err;
        }

        String sessionKey = req.getSessionKey();
        if (sessionKey == null || sessionKey.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_SESSION_KEY",
                    "Пустой sessionKey"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: empty session key");
            return err;
        }

        sessionKey = AuthKeyUtils.normalize(sessionKey, "sessionKey");
        try {
            AuthKeyUtils.parseEd25519PublicKey(sessionKey, "sessionKey");
        } catch (UnsupportedOperationException e) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    422,
                    "UNSUPPORTED_KEY_ALGORITHM",
                    "sessionKey prefix is not supported"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: unsupported session key algorithm");
            return err;
        } catch (IllegalArgumentException e) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_BASE64",
                    "Некорректный формат sessionKey"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: bad session key format");
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
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: empty signature");
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
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: time skew");
            return err;
        }

        String clientInfoFromClient = req.getClientInfo();
        if (clientInfoFromClient != null && clientInfoFromClient.length() > 50) {
            clientInfoFromClient = clientInfoFromClient.substring(0, 50);
        }

        String deviceKeyFromDb = user.getDeviceKey();
        if (deviceKeyFromDb == null || deviceKeyFromDb.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "NO_DEVICE_KEY",
                    "Отсутствует deviceKey у пользователя"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: no deviceKey");
            return err;
        }

        String authNonce = ctx.getAuthNonce();
        String authNonceFromReq = req.getAuthNonce();
        if (authNonceFromReq == null || authNonceFromReq.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_AUTH_NONCE",
                    "Пустой authNonce"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: empty authNonce");
            return err;
        }
        if (!authNonce.equals(authNonceFromReq)) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "AUTH_NONCE_MISMATCH",
                    "authNonce не соответствует контексту AuthChallenge"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: authNonce mismatch");
            return err;
        }

        String deviceKeyFromReq = req.getDeviceKey();
        if (deviceKeyFromReq == null || deviceKeyFromReq.isBlank()) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_DEVICE_KEY",
                    "Пустой deviceKey"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: empty deviceKey");
            return err;
        }
        deviceKeyFromReq = deviceKeyFromReq.trim();

        // TODO: для ротации device_key стоит дополнительно сверять актуальное значение через Solana.
        if (!deviceKeyFromReq.equals(deviceKeyFromDb)) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "DEVICE_KEY_NOT_ACTUAL",
                    "device_key не соответствует актуальной версии"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: device key mismatch");
            return err;
        }

        boolean sigOk;
        try {
            sigOk = verifyCreateSessionSignature(
                    loginFromReq,
                    sessionKey,
                    storagePwd,
                    authNonce,
                    timeMs,
                    deviceKeyFromDb,
                    signatureB64
            );
        } catch (UnsupportedOperationException ex) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    422,
                    "UNSUPPORTED_KEY_ALGORITHM",
                    "deviceKey algorithm is not supported"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: unsupported device key algorithm");
            return err;
        } catch (IllegalArgumentException ex) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_BASE64",
                    "Некорректный формат Base64 для ключа или подписи"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: bad base64");
            return err;
        }

        if (!sigOk) {
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "BAD_SIGNATURE",
                    "Подпись не прошла проверку"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: bad signature");
            return err;
        }

        // --- генерируем sessionId ---
        String sessionId = generateRandom32B64Url();
        long now = System.currentTimeMillis();

        // --- Сбор данных о клиенте (IP, UA, язык) ---
        Session wsSession = ctx.getWsSession();
        String clientInfoFromRequest = ClientInfoService.buildClientInfoString(wsSession);
        String userLanguage = ClientInfoService.extractPreferredLanguageTag(wsSession);

        String clientIp = "";
        if (wsSession != null) {
            String ip = ClientInfoService.extractClientIp(wsSession);
            if (ip != null) clientIp = ip;

            if (!clientIp.isBlank()) {
                try {
                    GeoLookupService.resolveCountryCityOrIpWithCache(clientIp);
                } catch (Exception e) {
                    log.debug("Geo lookup failed for ip={}", clientIp, e);
                }
            }
        }

        // --- создаём запись ActiveSession и сохраняем в БД ---
        ActiveSessionsDAO dao = ActiveSessionsDAO.getInstance();
        ActiveSessionEntry activeSessionEntry;

        try {
            activeSessionEntry = new ActiveSessionEntry(
                    sessionId,
                    canonicalLogin,
                    sessionKey,               // session_key (pubkey string as-is)
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
            log.error("Ошибка БД при создании новой сессии для login={}", canonicalLogin, e);
            Net_Response err = NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR_SESSION_CREATE",
                    "Ошибка БД при создании сессии"
            );
            closeConnectionAfterErrorResponse(ctx, 4001, "Auth failed: db error");
            return err;
        }

        // --- обновляем контекст ---
        ctx.setActiveSession(activeSessionEntry);
        ctx.setSessionId(sessionId);
        ctx.setAuthNonce(null);
        ctx.setAuthenticationStatus(ConnectionContext.AUTH_STATUS_USER);

        ActiveConnectionsRegistry.getInstance().register(ctx);

        // --- формируем ответ ---
        Net_CreateAuthSession_Response resp = new Net_CreateAuthSession_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setSessionId(sessionId);
        return resp;
    }

    private static boolean verifyCreateSessionSignature(
            String login,
            String sessionKey,
            String storagePwd,
            String authNonce,
            long timeMs,
            String deviceKey,
            String signatureB64
    ) throws IllegalArgumentException {

        byte[] publicKey32 = AuthKeyUtils.parseEd25519PublicKey(deviceKey, "deviceKey");
        byte[] signature64 = Base64Ws.decodeLen(signatureB64, 64, "signatureB64");

        String preimageStr = "AUTH_CREATE_SESSION:"
                + login + ":"
                + sessionKey + ":"
                + storagePwd + ":"
                + timeMs + ":"
                + authNonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);

        return Ed25519Util.verify(preimage, signature64, publicKey32);
    }

    private static String generateRandom32B64Url() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64Ws.encode(buf);
    }

    private static void closeConnectionAfterErrorResponse(ConnectionContext ctx, int statusCode, String reason) {
        if (ctx == null) return;
        new Thread(() -> {
            try {
                Thread.sleep(CLOSE_AFTER_ERROR_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            WsConnectionUtils.closeConnection(ctx, statusCode, reason);
        }, "CreateAuthSessionClose-" + System.identityHashCode(ctx)).start();
    }
}
