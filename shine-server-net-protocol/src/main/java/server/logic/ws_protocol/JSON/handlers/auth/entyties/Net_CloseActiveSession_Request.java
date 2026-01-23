package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос CloseActiveSession — закрытие активной сессии пользователя.
 *
 * Новая логика (v2):
 * - Доступно ТОЛЬКО после успешного входа в сессию (AUTH_STATUS_USER).
 * - Никаких подписей и "AUTH_IN_PROGRESS" здесь больше нет.
 *
 * payload:
 * {
 *   "sessionId": "..." // опционально; если пусто — закрываем текущую
 * }
 */
public class Net_CloseActiveSession_Request extends Net_Request {

    /** Идентификатор сессии, которую нужно закрыть. Может быть пустым. */
    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}