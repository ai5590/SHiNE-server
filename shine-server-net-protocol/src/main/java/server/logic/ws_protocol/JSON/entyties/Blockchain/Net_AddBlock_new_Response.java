package server.logic.ws_protocol.JSON.entyties.Blockchain;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

public class Net_AddBlock_new_Response extends Net_Response {

    private int serverLastGlobalNumber;
    private String serverLastGlobalHash;

    private int serverLastLineNumber;
    private String serverLastLineHash;

    private String reasonCode;   // "OUT_OF_SEQUENCE", "HASH_MISMATCH", ...

    public int getServerLastGlobalNumber() { return serverLastGlobalNumber; }
    public void setServerLastGlobalNumber(int v) { this.serverLastGlobalNumber = v; }

    public String getServerLastGlobalHash() { return serverLastGlobalHash; }
    public void setServerLastGlobalHash(String v) { this.serverLastGlobalHash = v; }

    public int getServerLastLineNumber() { return serverLastLineNumber; }
    public void setServerLastLineNumber(int v) { this.serverLastLineNumber = v; }

    public String getServerLastLineHash() { return serverLastLineHash; }
    public void setServerLastLineHash(String v) { this.serverLastLineHash = v; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
}