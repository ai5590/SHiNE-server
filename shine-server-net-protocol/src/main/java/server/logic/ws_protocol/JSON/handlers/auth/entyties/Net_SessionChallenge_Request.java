package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Шаг 1 входа в существующую сессию (v2):
 * SessionChallenge(sessionId) -> nonce
 */
public class Net_SessionChallenge_Request extends Net_Request {

    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}