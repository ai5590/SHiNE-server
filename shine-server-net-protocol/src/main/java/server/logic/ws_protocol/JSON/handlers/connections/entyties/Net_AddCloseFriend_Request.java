package server.logic.ws_protocol.JSON.handlers.connections.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_AddCloseFriend_Request extends Net_Request {
    private String toLogin;

    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }
}
