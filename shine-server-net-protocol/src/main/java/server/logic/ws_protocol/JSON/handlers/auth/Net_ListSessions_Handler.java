package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.*;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_ListSessions_Response.SessionInfo;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.entities.ActiveSessionEntry;
import shine.db.entities.SolanaUserEntry;
import shine.geo.GeoLookupService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Хэндлер ListSessions.
 *
 * Назначение:
 *  - вернуть список всех активных сессий текущего пользователя
 *    (по loginId из ctx/solanaUser).
 *
 * Безопасность:
 *  - анонимный клиент → NOT_AUTHENTICATED (UNVERIFIED);
 *  - AUTH_STATUS_USER → достаточно факта авторизации;
 *  - AUTH_STATUS_AUTH_IN_PROGRESS → требуется подпись, как в CreateAuthSession/CloseActiveSession.
 */
public class Net_ListSessions_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_ListSessions_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {
        Net_ListSessions_Request req = (Net_ListSessions_Request) baseReq;

        // 1) Проверяем, что вообще есть пользователь в контексте
        if (ctx == null || ctx.getSolanaUser() == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "NOT_AUTHENTICATED",
                    "Операция доступна только для авторизованных пользователей"
            );
        }

        SolanaUserEntry user = ctx.getSolanaUser();
        String currentLogin = user.getLogin();

        int authStatus = ctx.getAuthenticationStatus();
        if (authStatus != ConnectionContext.AUTH_STATUS_USER
                && authStatus != ConnectionContext.AUTH_STATUS_AUTH_IN_PROGRESS) {

            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "BAD_AUTH_STATUS",
                    "Операция ListSessions недоступна в текущем статусе аутентификации"
            );
        }

        // 2) Если мы ещё на шаге AUTH_IN_PROGRESS — проверяем подпись
        if (authStatus == ConnectionContext.AUTH_STATUS_AUTH_IN_PROGRESS) {
            String authNonce = ctx.getAuthNonce();
            if (authNonce == null) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "NO_STEP1_CONTEXT",
                        "Шаг 1 авторизации не был корректно выполнен для данного соединения"
                );
            }

            long timeMs = req.getTimeMs();
            String signatureB64 = req.getSignatureB64();

            if (signatureB64 == null || signatureB64.isBlank()) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "EMPTY_SIGNATURE",
                        "Подпись обязательна при статусе AUTH_IN_PROGRESS"
                );
            }

            long nowMs = System.currentTimeMillis();
            long diff = Math.abs(nowMs - timeMs);
            if (diff > Net_CreateAuthSession__Handler.ALLOWED_SKEW_MS) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "TIME_SKEW",
                        "Время клиента отличается от сервера более чем на 30 секунд"
                );
            }

            boolean sigOk;
            try {
                sigOk = Net_CreateAuthSession__Handler.verifyAuthorificatedSignature(
                        user,
                        authNonce,
                        timeMs,
                        signatureB64
                );
            } catch (IllegalArgumentException e) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "BAD_BASE64",
                        "Некорректный формат Base64 для ключа или подписи"
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
        }

        // 3) Тянем все активные сессии пользователя из БД
        List<ActiveSessionEntry> sessions;
        try {
            sessions = ActiveSessionsDAO.getInstance().getByLogin(currentLogin);
        } catch (SQLException e) {
            log.error("Ошибка БД при получении списка сессий для login={}", currentLogin, e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR_LIST_SESSIONS",
                    "Ошибка доступа к базе данных при получении списка сессий"
            );
        }

        // 4) Собираем DTO с геолокацией
        List<SessionInfo> resultList = new ArrayList<>();
        for (ActiveSessionEntry s : sessions) {
            SessionInfo info = new Net_ListSessions_Response.SessionInfo();
            info.setSessionId(s.getSessionId());
            info.setClientInfoFromClient(s.getClientInfoFromClient());
            info.setClientInfoFromRequest(s.getClientInfoFromRequest());
            info.setLastAuthirificatedAtMs(s.getLastAuthirificatedAtMs());

            String ip = s.getClientIp();
            String geo = GeoLookupService.resolveCountryCityOrIpWithCache(ip);
            info.setGeo(geo);

            resultList.add(info);
        }

        // 5) Формируем ответ
        Net_ListSessions_Response resp = new Net_ListSessions_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setSessions(resultList);

        return resp;
    }
}