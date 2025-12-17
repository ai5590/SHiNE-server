package server.logic.ws_protocol.JSON.entyties.Blockchain;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_AddBlock_new_Request extends Net_Request {

    private long blockchainId;

    private int globalNumber;
    private String prevGlobalHash; // HEX(64) or ""

    private int lineNumber;        // 0..7
    private int lineBlockNumber;
    private String prevLineHash;   // HEX(64) or ""

    private String blockBase64;    // base64url of raw .bch bytes

    public long getBlockchainId() { return blockchainId; }
    public void setBlockchainId(long blockchainId) { this.blockchainId = blockchainId; }

    public int getGlobalNumber() { return globalNumber; }
    public void setGlobalNumber(int globalNumber) { this.globalNumber = globalNumber; }

    public String getPrevGlobalHash() { return prevGlobalHash; }
    public void setPrevGlobalHash(String prevGlobalHash) { this.prevGlobalHash = prevGlobalHash; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public int getLineBlockNumber() { return lineBlockNumber; }
    public void setLineBlockNumber(int lineBlockNumber) { this.lineBlockNumber = lineBlockNumber; }

    public String getPrevLineHash() { return prevLineHash; }
    public void setPrevLineHash(String prevLineHash) { this.prevLineHash = prevLineHash; }

    public String getBlockBase64() { return blockBase64; }
    public void setBlockBase64(String blockBase64) { this.blockBase64 = blockBase64; }
}