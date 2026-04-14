package server.logic.ws_protocol.JSON.messages.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

public class Net_SendDirectMessage_Response extends Net_Response {
    private String messageId;
    private int deliveredWsSessions;
    private int deliveredWebPushSessions;
    private boolean sessionNotFound;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public int getDeliveredWsSessions() { return deliveredWsSessions; }
    public void setDeliveredWsSessions(int deliveredWsSessions) { this.deliveredWsSessions = deliveredWsSessions; }
    public int getDeliveredWebPushSessions() { return deliveredWebPushSessions; }
    public void setDeliveredWebPushSessions(int deliveredWebPushSessions) { this.deliveredWebPushSessions = deliveredWebPushSessions; }
    public boolean isSessionNotFound() { return sessionNotFound; }
    public void setSessionNotFound(boolean sessionNotFound) { this.sessionNotFound = sessionNotFound; }
}
