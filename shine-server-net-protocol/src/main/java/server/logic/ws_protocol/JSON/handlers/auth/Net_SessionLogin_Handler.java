package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_SessionLogin_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_SessionLogin_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.ActiveSessionEntry;
import shine.db.entities.SolanaUserEntry;
import shine.geo.ClientInfoService;
import shine.geo.GeoLookupService;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;

/**
 * SessionLogin (v2) — шаг 2 входа в существующую сессию (по sessionKey).
 *
 * Логика авторизации (v2):
 * - SessionChallenge(sessionId) выдаёт nonce (одноразовый, TTL).
 * - SessionLogin проверяет подпись sessionKey над строкой:
 *     SESSION_LOGIN:{sessionId}:{timeMs}:{nonce}
 * - sessionPubKey берём из БД: active_sessions.session_key (base64 32 bytes).
 *
 * При успехе:
 * - ctx становится AUTH_STATUS_USER
 * - обновляем метаданные сессии (lastAuth + clientIp + clientInfo + lang)
 * - возвращаем storagePwd
 */
public class Net_SessionLogin_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_SessionLogin_Handler.class);

    private static final long ALLOWED_SKEW_MS = 30_000L;

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {
        Net_SessionLogin_Request req = (Net_SessionLogin_Request) baseReq;

        String sessionId = req.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_SESSION_ID",
                    "Пустой sessionId"
            );
        }

        // проверка челленджа
        if (ctx.getSessionLoginNonce() == null
                || ctx.getSessionLoginSessionId() == null
                || System.currentTimeMillis() > ctx.getSessionLoginNonceExpiresAtMs()) {

            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "NO_CHALLENGE",
                    "Нет активного SessionChallenge или nonce истёк"
            );
        }

        if (!sessionId.equals(ctx.getSessionLoginSessionId())) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "SESSION_ID_MISMATCH",
                    "nonce был выдан для другого sessionId"
            );
        }

        long timeMs = req.getTimeMs();
        long nowMs = System.currentTimeMillis();
        if (Math.abs(nowMs - timeMs) > ALLOWED_SKEW_MS) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "TIME_SKEW",
                    "Время клиента отличается от сервера более чем на 30 секунд"
            );
        }

        String signatureB64 = req.getSignatureB64();
        if (signatureB64 == null || signatureB64.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_SIGNATURE",
                    "Пустая подпись"
            );
        }

        ActiveSessionEntry session;
        try {
            session = ActiveSessionsDAO.getInstance().getBySessionId(sessionId);
        } catch (SQLException e) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка доступа к базе данных"
            );
        }

        if (session == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "SESSION_NOT_FOUND",
                    "Сессия не найдена"
            );
        }

        String sessionPubKeyB64 = session.getSessionKey(); // это pubKey
        if (sessionPubKeyB64 == null || sessionPubKeyB64.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "NO_SESSION_KEY",
                    "В сессии не задан session_key"
            );
        }

        String nonce = ctx.getSessionLoginNonce();

        boolean sigOk;
        try {
            sigOk = verifySessionLoginSignature(sessionPubKeyB64, sessionId, timeMs, nonce, signatureB64);
        } catch (IllegalArgumentException e) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_BASE64",
                    "Некорректный Base64 для ключа/подписи"
            );
        }

        if (!sigOk) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "BAD_SIGNATURE",
                    "Подпись не прошла проверку"
            );
        }

        // сжигаем nonce
        ctx.setSessionLoginNonce(null);
        ctx.setSessionLoginSessionId(null);
        ctx.setSessionLoginNonceExpiresAtMs(0);

        // подтягиваем пользователя
        SolanaUserEntry user;
        try {
            user = SolanaUsersDAO.getInstance().getByLogin(session.getLogin());
        } catch (SQLException e) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR_USER_LOOKUP",
                    "Ошибка доступа к базе данных при получении пользователя"
            );
        }

        if (user == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "USER_NOT_FOUND_FOR_SESSION",
                    "Пользователь для данной сессии не найден"
            );
        }

        // обновление метаданных
        String clientInfoFromClient = req.getClientInfo();
        if (clientInfoFromClient != null && clientInfoFromClient.length() > 50) {
            clientInfoFromClient = clientInfoFromClient.substring(0, 50);
        }

        String clientIp = null;
        String clientInfoFromRequest = null;
        String userLanguage = null;

        if (ctx.getWsSession() != null) {
            clientIp = ClientInfoService.extractClientIp(ctx.getWsSession());
            clientInfoFromRequest = ClientInfoService.buildClientInfoString(ctx.getWsSession());
            userLanguage = ClientInfoService.extractPreferredLanguageTag(ctx.getWsSession());

            if (clientIp != null && !clientIp.isBlank()) {
                try {
                    GeoLookupService.resolveCountryCityOrIpWithCache(clientIp);
                } catch (Exception e) {
                    log.debug("Geo lookup failed for ip={}", clientIp, e);
                }
            }
        }

        long now = System.currentTimeMillis();
        try {
            ActiveSessionsDAO.getInstance().updateOnRefresh(
                    sessionId,
                    now,
                    clientIp,
                    clientInfoFromClient,
                    clientInfoFromRequest,
                    userLanguage
            );
        } catch (SQLException e) {
            log.error("Ошибка БД при updateOnRefresh sessionId={}", sessionId, e);
        }

        session.setLastAuthirificatedAtMs(now);
        session.setClientIp(clientIp);
        session.setClientInfoFromClient(clientInfoFromClient);
        session.setClientInfoFromRequest(clientInfoFromRequest);
        session.setUserLanguage(userLanguage);

        // ctx
        ctx.setActiveSession(session);
        ctx.setSolanaUser(user);
        ctx.setSessionId(sessionId);
        ctx.setAuthenticationStatus(ConnectionContext.AUTH_STATUS_USER);

        ActiveConnectionsRegistry.getInstance().register(ctx);

        // ответ
        Net_SessionLogin_Response resp = new Net_SessionLogin_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setStoragePwd(session.getStoragePwd());
        return resp;
    }

    private static boolean verifySessionLoginSignature(
            String sessionPubKeyB64,
            String sessionId,
            long timeMs,
            String nonce,
            String signatureB64
    ) throws IllegalArgumentException {

        byte[] publicKey32 = Ed25519Util.keyFromBase64(sessionPubKeyB64);
        byte[] signature64 = decodeBase64Any(signatureB64);

        String preimageStr = "SESSION_LOGIN:" + sessionId + ":" + timeMs + ":" + nonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);

        return Ed25519Util.verify(preimage, signature64, publicKey32);
    }

    private static byte[] decodeBase64Any(String s) throws IllegalArgumentException {
        try {
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException ignore) {
            return Base64.getDecoder().decode(s);
        }
    }
}