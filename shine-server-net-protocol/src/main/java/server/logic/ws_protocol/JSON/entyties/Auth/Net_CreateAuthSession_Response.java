package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetResponse;

/**
 * Ответ на CreateAuthSession.
 *
 * При успехе сервер создаёт запись в active_sessions
 * и возвращает идентификатор сессии sessionId и секрет сессии sessionPwd.
 *
 * JSON:
 * {
 *   "op": "CreateAuthSession",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "sessionId": "base64-строка-от-32-байт",
 *     "sessionPwd": "base64-строка-от-32-байт"
 *   }
 * }
 */
public class Net_CreateAuthSession_Response extends NetResponse {

    /** Идентификатор сессии, base64 от 32 байт. */
    private String sessionId;

    /** Секрет сессии, base64 от 32 байт. */
    private String sessionPwd;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionPwd() {
        return sessionPwd;
    }

    public void setSessionPwd(String sessionPwd) {
        this.sessionPwd = sessionPwd;
    }
}