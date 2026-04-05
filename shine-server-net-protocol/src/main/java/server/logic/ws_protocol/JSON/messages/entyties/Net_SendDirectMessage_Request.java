package server.logic.ws_protocol.JSON.messages.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_SendDirectMessage_Request extends Net_Request {
    private String toLogin;
    private String text;

    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
