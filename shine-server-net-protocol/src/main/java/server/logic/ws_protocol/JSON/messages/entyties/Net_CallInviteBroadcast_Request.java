package server.logic.ws_protocol.JSON.messages.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_CallInviteBroadcast_Request extends Net_Request {
    private String toLogin;
    private String callId;
    private Integer type;

    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
}
