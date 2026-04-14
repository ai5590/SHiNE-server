package server.logic.ws_protocol.JSON.messages.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_SendDirectMessage_Request extends Net_Request {
    private String blobB64;

    public String getBlobB64() { return blobB64; }
    public void setBlobB64(String blobB64) { this.blobB64 = blobB64; }
}
