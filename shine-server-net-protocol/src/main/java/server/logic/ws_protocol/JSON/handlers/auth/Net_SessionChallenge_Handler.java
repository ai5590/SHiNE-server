package server.logic.ws_protocol.JSON.handlers.auth;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_SessionChallenge_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_SessionChallenge_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.entities.ActiveSessionEntry;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;

/**
 * SessionChallenge (v2) — шаг 1 входа в существующую сессию.
 *
 * Логика авторизации (v2):
 * - Вход в существующую сессию ВСЕГДА в 2 шага:
 *   1) SessionChallenge(sessionId) -> nonce
 *   2) SessionLogin(sessionId, timeMs, signature(sessionKey, SESSION_LOGIN:...))
 *
 * Что делает:
 * - Проверяет, что sessionId существует в БД.
 * - Генерирует одноразовый nonce (base64url(32)), сохраняет его в ctx:
 *   ctx.sessionLoginNonce, ctx.sessionLoginSessionId, ctx.sessionLoginNonceExpiresAtMs.
 */
public class Net_SessionChallenge_Handler implements JsonMessageHandler {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long NONCE_TTL_MS = 60_000L;

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {
        Net_SessionChallenge_Request req = (Net_SessionChallenge_Request) baseReq;

        String sessionId = req.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_SESSION_ID",
                    "Пустой sessionId"
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

        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        long now = System.currentTimeMillis();
        ctx.setSessionLoginNonce(nonce);
        ctx.setSessionLoginSessionId(sessionId);
        ctx.setSessionLoginNonceExpiresAtMs(now + NONCE_TTL_MS);

        Net_SessionChallenge_Response resp = new Net_SessionChallenge_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setNonce(nonce);
        return resp;
    }
}