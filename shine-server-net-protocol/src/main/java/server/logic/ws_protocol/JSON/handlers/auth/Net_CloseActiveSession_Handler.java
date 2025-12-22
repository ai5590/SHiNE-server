package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_CloseActiveSession_Request;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_CloseActiveSession_Response;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import server.ws.WsConnectionUtils;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.entities.ActiveSessionEntry;
import shine.db.entities.SolanaUserEntry;

import java.sql.SQLException;

/**
 * Хэндлер CloseActiveSession.
 *
 * Назначение:
 *  - закрыть одну из активных сессий пользователя:
 *      * либо явно указанную в sessionId,
 *      * либо текущую (если sessionId не задана).
 *
 * Допустимые состояния:
 *  - AUTH_STATUS_USER:
 *      * timeMs / signatureB64 могут быть пустыми.
 *      * Достаточно факта текущей авторизации.
 *
 *  - AUTH_STATUS_AUTH_IN_PROGRESS:
 *      * требуется проверка подписи Ed25519 над строкой
 *        "AUTHORIFICATED:" + timeMs + authNonce
 *        (authNonce взят на шаге AuthChallenge и хранится в ctx.authNonce).
 *      * Если подпись корректна, можно закрывать сессию даже до полноценной
 *        установки новой сессии.
 *
 * Закрытие:
 *  - запись ActiveSession удаляется из БД;
 *  - если по этой sessionId есть активное WebSocket-подключение:
 *      * если это ДРУГОЕ подключение — оно закрывается сразу;
 *      * если это ТЕКУЩЕЕ подключение — сначала отправляется ответ 200,
 *        а закрытие выполняется в отдельном потоке с небольшой задержкой.
 */
public class Net_CloseActiveSession_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_CloseActiveSession_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {
        Net_CloseActiveSession_Request req = (Net_CloseActiveSession_Request) baseReq;

        if (ctx == null || ctx.getSolanaUser() == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "NOT_AUTHENTICATED",
                    "Операция доступна только в состоянии авторизации или авторификации"
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
                    "Операция CloseActiveSession недоступна в текущем статусе аутентификации"
            );
        }

        // Если мы ещё на шаге AUTH_IN_PROGRESS — проверяем подпись
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

        // Определяем, какую sessionId закрывать
        String targetSessionId = req.getSessionId();
        if (targetSessionId == null || targetSessionId.isBlank()) {
            // Если sessionId не передана — берём текущую активную
            if (ctx.getActiveSession() != null && ctx.getActiveSession().getSessionId() != null) {
                targetSessionId = ctx.getActiveSession().getSessionId();
            } else if (ctx.getSessionId() != null) {
                targetSessionId = ctx.getSessionId();
            } else {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "NO_SESSION_TO_CLOSE",
                        "Не удалось определить, какую сессию нужно закрыть"
                );
            }
        }

        ActiveSessionsDAO sessionsDao = ActiveSessionsDAO.getInstance();
        ActiveSessionEntry targetSession;
        try {
            targetSession = sessionsDao.getBySessionId(targetSessionId);
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

        // Пытаемся удалить сессию из БД и закрыть соответствующее подключение
        closeActiveSession(targetSessionId, ctx, isCurrentSession);

        // Ответ OK (payload станет {} в JsonInboundProcessor)
        Net_CloseActiveSession_Response resp = new Net_CloseActiveSession_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);

        // Для текущей сессии WebSocket будет закрыт чуть позже в отдельном потоке,
        // чтобы этот ответ успел уйти.
        return resp;
    }

    /**
     * Закрытие активной сессии:
     *  - удаление записи из БД;
     *  - закрытие WebSocket-подключения, если оно существует.
     *
     * @param targetSessionId  идентификатор сессии, которую надо закрыть
     * @param currentCtx       контекст текущего подключения (которое вызвало запрос)
     * @param isCurrentSession true, если закрывается "эта же" сессия
     */
    private void closeActiveSession(String targetSessionId,
                                    ConnectionContext currentCtx,
                                    boolean isCurrentSession) {

        ActiveSessionsDAO sessionsDao = ActiveSessionsDAO.getInstance();
        try {
            sessionsDao.deleteBySessionId(targetSessionId);
        } catch (SQLException e) {
            log.error("Ошибка БД при удалении сессии sessionId={}", targetSessionId, e);
            // Логируем, но считаем, что для клиента сессия всё равно должна быть недействительна.
        }

        ConnectionContext ctxToClose =
                ActiveConnectionsRegistry.getInstance().getBySessionId(targetSessionId);

        if (ctxToClose == null) {
            return;
        }

        if (isCurrentSession && ctxToClose == currentCtx) {
            // Это текущее подключение: закрываем после отправки ответа.
            new Thread(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                WsConnectionUtils.closeConnection(
                        ctxToClose,
                        4000,
                        "Session closed by client via CloseActiveSession"
                );
            }, "CloseSession-" + targetSessionId).start();
        } else {
            // Другая сессия — можно закрыть сразу
            WsConnectionUtils.closeConnection(
                    ctxToClose,
                    4000,
                    "Session closed by client via CloseActiveSession"
            );
        }
    }
}