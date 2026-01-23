package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_CloseActiveSession_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_CloseActiveSession_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import server.ws.WsConnectionUtils;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.entities.ActiveSessionEntry;
import shine.db.entities.SolanaUserEntry;

import java.sql.SQLException;

/**
 * CloseActiveSession (v2) — закрытие текущей или другой сессии.
 *
 * Логика авторизации (v2):
 * - Доступно ТОЛЬКО после успешного входа в сессию (AUTH_STATUS_USER).
 * - Никаких подписей и AUTH_IN_PROGRESS здесь больше нет.
 *
 * Закрытие:
 * - удаляем запись из БД
 * - если по sessionId есть активный WS — закрываем его
 */
public class Net_CloseActiveSession_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_CloseActiveSession_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {
        Net_CloseActiveSession_Request req = (Net_CloseActiveSession_Request) baseReq;

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

        String targetSessionId = req.getSessionId();
        if (targetSessionId == null || targetSessionId.isBlank()) {
            if (ctx.getSessionId() != null && !ctx.getSessionId().isBlank()) {
                targetSessionId = ctx.getSessionId();
            } else if (ctx.getActiveSession() != null && ctx.getActiveSession().getSessionId() != null) {
                targetSessionId = ctx.getActiveSession().getSessionId();
            } else {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "NO_SESSION_TO_CLOSE",
                        "Не удалось определить, какую сессию нужно закрыть"
                );
            }
        }

        ActiveSessionEntry targetSession;
        try {
            targetSession = ActiveSessionsDAO.getInstance().getBySessionId(targetSessionId);
        } catch (SQLException e) {
            log.error("Ошибка БД при поиске сессии для CloseActiveSession sessionId={}", targetSessionId, e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка доступа к базе данных при поиске сессии"
            );
        }

        if (targetSession == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "SESSION_NOT_FOUND",
                    "Сессия для закрытия не найдена"
            );
        }

        if (currentLogin == null || !currentLogin.equals(targetSession.getLogin())) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "SESSION_OF_ANOTHER_USER",
                    "Нельзя закрывать сессию другого пользователя"
            );
        }

        boolean isCurrentSession = targetSessionId.equals(ctx.getSessionId());

        closeActiveSession(targetSessionId, ctx, isCurrentSession);

        Net_CloseActiveSession_Response resp = new Net_CloseActiveSession_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        return resp;
    }

    private void closeActiveSession(String targetSessionId,
                                    ConnectionContext currentCtx,
                                    boolean isCurrentSession) {

        try {
            ActiveSessionsDAO.getInstance().deleteBySessionId(targetSessionId);
        } catch (SQLException e) {
            log.error("Ошибка БД при удалении сессии sessionId={}", targetSessionId, e);
        }

        ConnectionContext ctxToClose =
                ActiveConnectionsRegistry.getInstance().getBySessionId(targetSessionId);

        if (ctxToClose == null) return;

        if (isCurrentSession && ctxToClose == currentCtx) {
            new Thread(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                WsConnectionUtils.closeConnection(
                        ctxToClose,
                        4000,
                        "Session closed by client via CloseActiveSession"
                );
            }, "CloseSession-" + targetSessionId).start();
        } else {
            WsConnectionUtils.closeConnection(
                    ctxToClose,
                    4000,
                    "Session closed by client via CloseActiveSession"
            );
        }
    }
}