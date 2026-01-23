package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Ответ на SessionLogin (v2).
 * payload: { "storagePwd": "base64url(32)" }
 */
public class Net_SessionLogin_Response extends Net_Response {

    private String storagePwd;

    public String getStoragePwd() {
        return storagePwd;
    }

    public void setStoragePwd(String storagePwd) {
        this.storagePwd = storagePwd;
    }
}