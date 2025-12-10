package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_CreateAuthSession_Response;
import server.logic.ws_protocol.JSON.entyties.NetRequest;
import server.logic.ws_protocol.JSON.entyties.NetResponse;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_CreateAuthSession_Request;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.entities.ActiveSession;
import shine.db.entities.SolanaUser;
import shine.geo.ClientInfoService;
import utils.crypto.Ed25519Util;

import org.eclipse.jetty.websocket.api.Session;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Шаг 2 авторизации: проверка подписи и создание сессии.
 *
 * Клиент присылает в payload:
 *  - storagePwd    (base64 от 32 байт)
 *  - timeMs        (long, мс с 1970-01-01)
 *  - signatureB64  (подпись Ed25519 над строкой:
 *                   "AUTHORIFICATED:" + timeMs + authNonce)
 *  - clientInfo    (опционально, до 50 символов)
 *
 * authNonce клиент получил на шаге 1 (AuthChallenge).
 *
 * При успехе:
 *  - создаётся запись ActiveSession в БД;
 *  - генерируется sessionId (base64 от 32 случайных байт);
 *  - генерируется sessionPwd (base64 от 32 случайных байт);
 *  - sessionCreatedAtMs и lastAuthirificatedAtMs = текущее время;
 *  - заполняются поля clientIp, clientInfoFromClient, clientInfoFromRequest, userLanguage;
 *  - возвращается sessionId и sessionPwd в ответе.
 */
public class Net_CreateAuthSession__Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_CreateAuthSession__Handler.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long ALLOWED_SKEW_MS = 30_000L;

    @Override
    public NetResponse handle(NetRequest baseReq, ConnectionContext ctx) throws Exception {
        Net_CreateAuthSession_Request req = (Net_CreateAuthSession_Request) baseReq;

        // --- базовые проверки контекста ---
        if (ctx == null || ctx.getSolanaUser() == null || ctx.getSessionPwd() == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "NO_STEP1_CONTEXT",
                    "Шаг 1 авторизации не был корректно выполнен для данного соединения"
            );
        }

        if (!ctx.isAnonymous()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "ALREADY_AUTHED",
                    "Пользователь уже авторизован по текущему соединению"
            );
        }

        SolanaUser user = ctx.getSolanaUser();
        Long loginId = user.getLoginId();
        if (loginId == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "NO_LOGIN_ID",
                    "Для пользователя не задан loginId в БД"
            );
        }

        String storagePwd = req.getStoragePwd();
        if (storagePwd == null || storagePwd.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_STORAGE_PWD",
                    "Пустой storagePwd"
            );
        }

        String signatureB64 = req.getSignatureB64();
        if (signatureB64 == null || signatureB64.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_SIGNATURE",
                    "Пустая цифровая подпись"
            );
        }

        long timeMs = req.getTimeMs();
        long nowMs = System.currentTimeMillis();

        long diff = Math.abs(nowMs - timeMs);
        if (diff > ALLOWED_SKEW_MS) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "TIME_SKEW",
                    "Время клиента отличается от сервера более чем на 30 секунд"
            );
        }

        // Короткая строка clientInfo от клиента (до 50 символов)
        String clientInfoFromClient = req.getClientInfo();
        if (clientInfoFromClient != null && clientInfoFromClient.length() > 50) {
            clientInfoFromClient = clientInfoFromClient.substring(0, 50);
        }

        // --- выбираем публичный ключ pubkey1 ---
        String pubKeyB64 = user.getDeviceKey();
        if (pubKeyB64 == null || pubKeyB64.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "NO_PUBKEY1",
                    "Отсутствует публичный ключ pubkey1 для пользователя"
            );
        }

        byte[] publicKey32;
        byte[] signature64;
        try {
            publicKey32 = Ed25519Util.keyFromBase64(pubKeyB64);
            signature64 = Base64.getDecoder().decode(signatureB64);
        } catch (IllegalArgumentException ex) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_BASE64",
                    "Некорректный формат Base64 для ключа или подписи"
            );
        }

        // --- authNonce (challenge) мы сохранили в ctx.sessionPwd на шаге 1 ---
        String authNonce = ctx.getSessionPwd();

        // --- собираем строку для подписи: "AUTHORIFICATED:" + timeMs + authNonce ---
        String preimageStr = "AUTHORIFICATED:" + timeMs + authNonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);

        boolean sigOk = Ed25519Util.verify(preimage, signature64, publicKey32);
        if (!sigOk) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "BAD_SIGNATURE",
                    "Подпись не прошла проверку"
            );
        }

        // --- Генерируем настоящий секрет сессии (sessionPwd) и sessionId ---
        String newSessionPwd = generateRandomSecret();
        String sessionId = generateRandomSessionId();
        long now = System.currentTimeMillis();

        // --- Сбор данных о клиенте (IP, UA, язык) ---
        Session wsSession = ctx.getWsSession();
        String clientInfoFromRequest = ClientInfoService.buildClientInfoString(wsSession);
        String userLanguage = ClientInfoService.extractPreferredLanguageTag(wsSession);

        String clientIp = "";
        if (wsSession != null) {
            SocketAddress rawAddr = wsSession.getRemoteAddress();
            if (rawAddr instanceof InetSocketAddress inet) {
                if (inet.getAddress() != null) {
                    clientIp = inet.getAddress().getHostAddress();
                }
            }
        }
// TODO и сдесь тоже переписываем получение ИП адреса на стандартный метод и тоже дёргаем запрос геолокации который никуда не сохраняем просто что бы он в кэш сервера попал


        // --- создаём запись ActiveSession и сохраняем в БД ---
        ActiveSessionsDAO dao = ActiveSessionsDAO.getInstance();
        ActiveSession activeSession;

        try {
            activeSession = new ActiveSession(
                    sessionId,
                    loginId,
                    newSessionPwd,           // настоящий секрет сессии
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

            dao.insert(activeSession);
        } catch (SQLException e) {
            log.error("Ошибка БД при создании новой сессии для loginId={}", loginId, e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR_SESSION_CREATE",
                    "Ошибка БД при создании сессии"
            );
        }

        // --- обновляем контекст ---
        ctx.setActiveSession(activeSession);
        ctx.setSessionId(sessionId);
        ctx.setSessionPwd(newSessionPwd);  // теперь в контексте хранится секрет сессии, а не authNonce
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