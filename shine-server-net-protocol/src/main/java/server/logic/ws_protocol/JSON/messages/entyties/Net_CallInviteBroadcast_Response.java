package server.logic.ws_protocol.JSON.messages.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

public class Net_CallInviteBroadcast_Response extends Net_Response {
    private String callId;
    private int deliveredWsSessions;
    private int deliveredFcmSessions;

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public int getDeliveredWsSessions() { return deliveredWsSessions; }
    public void setDeliveredWsSessions(int deliveredWsSessions) { this.deliveredWsSessions = deliveredWsSessions; }

    public int getDeliveredFcmSessions() { return deliveredFcmSessions; }
    public void setDeliveredFcmSessions(int deliveredFcmSessions) { this.deliveredFcmSessions = deliveredFcmSessions; }
}
