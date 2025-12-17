package server.logic.ws_protocol.JSON.entyties.Blockchain;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * AddBlock_new response.
 *
 * payload:
 *  - accepted (true/false)
 *  - newGlobalNumber
 *  - newGlobalHashHex
 *  - newLineNumber
 *  - newLineHashHex
 *  - sizeBytes
 */
public class Net_AddBlock_new_Response extends Net_Response {

    private boolean accepted;

    private int newGlobalNumber;
    private String newGlobalHashHex;

    private int newLineNumber;
    private String newLineHashHex;

    private int sizeBytes;

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }

    public int getNewGlobalNumber() { return newGlobalNumber; }
    public void setNewGlobalNumber(int newGlobalNumber) { this.newGlobalNumber = newGlobalNumber; }

    public String getNewGlobalHashHex() { return newGlobalHashHex; }
    public void setNewGlobalHashHex(String newGlobalHashHex) { this.newGlobalHashHex = newGlobalHashHex; }

    public int getNewLineNumber() { return newLineNumber; }
    public void setNewLineNumber(int newLineNumber) { this.newLineNumber = newLineNumber; }

    public String getNewLineHashHex() { return newLineHashHex; }
    public void setNewLineHashHex(String newLineHashHex) { this.newLineHashHex = newLineHashHex; }

    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int sizeBytes) { this.sizeBytes = sizeBytes; }
}