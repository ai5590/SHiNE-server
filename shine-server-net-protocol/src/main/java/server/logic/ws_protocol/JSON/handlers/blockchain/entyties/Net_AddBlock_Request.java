package server.logic.ws_protocol.JSON.handlers.blockchain.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public final class Net_AddBlock_Request extends Net_Request {

    private String blockchainName;   // обязателен
    private int blockNumber;         // обязателен
    private String prevBlockHash;    // HEX(64) или "" для нулевого
    private String blockBytesB64;    // байты FULL-блока (raw+sig+hash) в Base64

    public String getBlockchainName() { return blockchainName; }
    public void setBlockchainName(String blockchainName) { this.blockchainName = blockchainName; }

    public int getBlockNumber() { return blockNumber; }
    public void setBlockNumber(int blockNumber) { this.blockNumber = blockNumber; }

    public String getPrevBlockHash() { return prevBlockHash; }
    public void setPrevBlockHash(String prevBlockHash) { this.prevBlockHash = prevBlockHash; }

    public String getBlockBytesB64() { return blockBytesB64; }
    public void setBlockBytesB64(String blockBytesB64) { this.blockBytesB64 = blockBytesB64; }
}