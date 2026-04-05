package server.logic.ws_protocol.JSON.messages.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

public class Net_SendDirectMessage_Response extends Net_Response {
    private String messageId;
    private int deliveredWsSessions;
    private int deliveredFcmSessions;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public int getDeliveredWsSessions() { return deliveredWsSessions; }
    public void setDeliveredWsSessions(int deliveredWsSessions) { this.deliveredWsSessions = deliveredWsSessions; }
    public int getDeliveredFcmSessions() { return deliveredFcmSessions; }
    public void setDeliveredFcmSessions(int deliveredFcmSessions) { this.deliveredFcmSessions = deliveredFcmSessions; }
}
