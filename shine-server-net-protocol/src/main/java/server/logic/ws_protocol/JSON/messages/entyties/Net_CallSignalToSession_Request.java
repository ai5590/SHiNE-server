package server.logic.ws_protocol.JSON.messages.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_CallSignalToSession_Request extends Net_Request {
    private String toLogin;
    private String targetSessionId;
    private String callId;
    private Integer type;
    private String data;

    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }

    public String getTargetSessionId() { return targetSessionId; }
    public void setTargetSessionId(String targetSessionId) { this.targetSessionId = targetSessionId; }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
