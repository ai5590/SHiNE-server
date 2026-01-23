package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_ListSessions_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_ListSessions_Response;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_ListSessions_Response.SessionInfo;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.entities.ActiveSessionEntry;
import shine.db.entities.SolanaUserEntry;
import shine.geo.GeoLookupService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ListSessions (v2) — список активных сессий.
 *
 * Логика авторизации (v2):
 * - Доступно ТОЛЬКО после успешного входа в сессию (AUTH_STATUS_USER).
 * - Никаких подписей здесь больше нет.
 */
public class Net_ListSessions_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_ListSessions_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {
        Net_ListSessions_Request req = (Net_ListSessions_Request) baseReq;

        if (ctx == null || ctx.getSolanaUser() == null || ctx.getAuthenticationStatus() != ConnectionContext.AUTH_STATUS_USER) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "NOT_AUTHENTICATED",
                    "Операция доступна только для авторизованных пользователей"
            );
        }

        SolanaUserEntry user = ctx.getSolanaUser();
        String currentLogin = user.getLogin();

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

        List<SessionInfo> resultList = new ArrayList<>();
        for (ActiveSessionEntry s : sessions) {
            SessionInfo info = new SessionInfo();
            info.setSessionId(s.getSessionId());
            info.setClientInfoFromClient(s.getClientInfoFromClient());
            info.setClientInfoFromRequest(s.getClientInfoFromRequest());
            info.setLastAuthirificatedAtMs(s.getLastAuthirificatedAtMs());

            String ip = s.getClientIp();
            String geo = GeoLookupService.resolveCountryCityOrIpWithCache(ip);
            info.setGeo(geo);

            resultList.add(info);
        }

        Net_ListSessions_Response resp = new Net_ListSessions_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setSessions(resultList);

        return resp;
    }
}