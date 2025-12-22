package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_RefreshSession_Request;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_RefreshSession_Response;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.ActiveSessionEntry;
import shine.db.entities.SolanaUserEntry;
import shine.geo.ClientInfoService;
import shine.geo.GeoLookupService;

import java.sql.SQLException;

/**
 * Хэндлер RefreshSession.
 *
 * При успешной проверке sessionId + sessionPwd:
 *  - подтягивает пользователя по loginId из сессии;
 *  - заполняет ConnectionContext;
 *  - обновляет lastAuthirificatedAtMs и метаданные сессии в БД;
 *  - возвращает storagePwd в payload.
 */
public class Net_RefreshSession_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_RefreshSession_Handler.class);

    // максимум 50 символов для clientInfo от клиента
    private static final int CLIENT_INFO_MAX_LEN = 50;

    @Override
    public Net_Response handle(Net_Request request, ConnectionContext ctx) throws Exception {
        Net_RefreshSession_Request req = (Net_RefreshSession_Request) request;

        String sessionId = req.getSessionId();
        String sessionPwd = req.getSessionPwd();
        String clientInfoFromClient = trimClientInfo(req.getClientInfo());

        if (sessionId == null || sessionId.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_SESSION_ID",
                    "Пустой идентификатор сессии"
            );
        }

        if (sessionPwd == null || sessionPwd.isEmpty()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_SESSION_PWD",
                    "Пустой пароль сессии"
            );
        }

        ActiveSessionsDAO sessionsDao = ActiveSessionsDAO.getInstance();
        ActiveSessionEntry session;
        try {
            session = sessionsDao.getBySessionId(sessionId);
        } catch (SQLException e) {
            log.error("Ошибка БД при поиске сессии sessionId={}", sessionId, e);
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

        String dbPwd = session.getSessionPwd();
        if (dbPwd == null || !dbPwd.equals(sessionPwd)) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "SESSION_PWD_MISMATCH",
                    "Неверный пароль сессии"
            );
        }

        // --- вытаскиваем пользователя по login из сессии ---
        SolanaUserEntry solanaUserEntry;
        String login = session.getLogin();

        try {
            SolanaUsersDAO usersDao = SolanaUsersDAO.getInstance();
            solanaUserEntry = usersDao.getByLogin(login);
        } catch (SQLException e) {
            log.error("Ошибка БД при поиске пользователя по login={} из сессии", login, e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR_USER_LOOKUP",
                    "Ошибка доступа к базе данных при получении пользователя для сессии"
            );
        }

        if (solanaUserEntry == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "USER_NOT_FOUND_FOR_SESSION",
                    "Пользователь для данной сессии не найден"
            );
        }

        // --- собираем данные о клиенте из WebSocket-запроса ---
        String clientIp = null;
        String clientInfoFromRequest = null;
        String userLanguage = null;

        if (ctx != null && ctx.getWsSession() != null) {
            // Нормальное получение IP-адреса клиента
            clientIp = ClientInfoService.extractClientIp(ctx.getWsSession());

            // Сделать запрос геолокации и никуда её не сохранять:
            // вызов с кэшированием в БД, нужно только для прогрева кэша.
            if (clientIp != null && !clientIp.isBlank()) {
                try {
                    GeoLookupService.resolveCountryCityOrIpWithCache(clientIp);
                } catch (Exception e) {
                    // Геолокация не критична, просто логируем на debug/trace при желании
                    log.debug("Geo lookup failed for ip={}", clientIp, e);
                }
            }

            clientInfoFromRequest = ClientInfoService.buildClientInfoString(ctx.getWsSession());
            userLanguage = ClientInfoService.extractPreferredLanguageTag(ctx.getWsSession());
        }

        long nowMs = System.currentTimeMillis();

        // --- обновляем запись в БД (lastAuth + мета) ---
        try {
            sessionsDao.updateOnRefresh(
                    sessionId,
                    nowMs,
                    clientIp,
                    clientInfoFromClient,
                    clientInfoFromRequest,
                    userLanguage
            );
        } catch (SQLException e) {
            log.error("Ошибка БД при обновлении метаданных сессии sessionId={}", sessionId, e);
            // не роняем авторизацию, но логируем
        }

        // Также обновим объект session в памяти (если дальше кто-то его использует)
        session.setLastAuthirificatedAtMs(nowMs);
        session.setClientIp(clientIp);
        session.setClientInfoFromClient(clientInfoFromClient);
        session.setClientInfoFromRequest(clientInfoFromRequest);
        session.setUserLanguage(userLanguage);

        // --- обновляем контекст соединения ---
        if (ctx != null) {
            ctx.setActiveSession(session);
            ctx.setSolanaUser(solanaUserEntry);
            ctx.setSessionId(sessionId);
            ctx.setSessionPwd(sessionPwd);
            ctx.setAuthenticationStatus(ConnectionContext.AUTH_STATUS_USER);

            // Регистрируем это подключение в глобальном реестре активных соединений
            ActiveConnectionsRegistry.getInstance().register(ctx);
        }

        // --- ответ OK + storagePwd ---
        Net_RefreshSession_Response resp = new Net_RefreshSession_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setStoragePwd(session.getStoragePwd());
        return resp;
    }

    private String trimClientInfo(String info) {
        if (info == null) return null;
        info = info.trim();
        if (info.length() > CLIENT_INFO_MAX_LEN) {
            return info.substring(0, CLIENT_INFO_MAX_LEN);
        }
        return info;
    }
}