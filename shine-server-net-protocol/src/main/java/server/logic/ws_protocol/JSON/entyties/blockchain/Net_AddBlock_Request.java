package server.logic.ws_protocol.JSON.entyties.blockchain;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public final class Net_AddBlock_Request extends Net_Request {

    private String blockchainName;        // обязателен
    private int globalNumber;             // обязателен
    private String prevGlobalHash;        // HEX(64) или "" для нулевого
    private String blockBytesB64;         // байты FULL-блока (raw+sig+hash) в Base64

    public String getBlockchainName() { return blockchainName; }
    public void setBlockchainName(String blockchainName) { this.blockchainName = blockchainName; }

    public int getGlobalNumber() { return globalNumber; }
    public void setGlobalNumber(int globalNumber) { this.globalNumber = globalNumber; }

    public String getPrevGlobalHash() { return prevGlobalHash; }
    public void setPrevGlobalHash(String prevGlobalHash) { this.prevGlobalHash = prevGlobalHash; }

    public String getBlockBytesB64() { return blockBytesB64; }
    public void setBlockBytesB64(String blockBytesB64) { this.blockBytesB64 = blockBytesB64; }
}