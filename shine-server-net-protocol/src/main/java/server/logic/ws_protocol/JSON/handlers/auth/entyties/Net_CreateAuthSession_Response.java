package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Ответ на CreateAuthSession (v2).
 *
 * При успехе сервер создаёт запись в active_sessions
 * и возвращает идентификатор сессии sessionId.
 *
 * JSON:
 * {
 *   "op": "CreateAuthSession",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "sessionId": "base64url(32)"
 *   }
 * }
 */
public class Net_CreateAuthSession_Response extends Net_Response {

    /** Идентификатор сессии, base64url от 32 байт. */
    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}