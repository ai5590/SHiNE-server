package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Ответ на SessionChallenge (v2).
 * payload: { "nonce": "base64url(32)" }
 */
public class Net_SessionChallenge_Response extends Net_Response {

    private String nonce;

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }
}