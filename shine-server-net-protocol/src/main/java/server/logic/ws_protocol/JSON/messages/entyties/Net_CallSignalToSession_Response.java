package server.logic.ws_protocol.JSON.messages.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

public class Net_CallSignalToSession_Response extends Net_Response {
    private boolean delivered;

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }
}
