package server.logic.ws_protocol.JSON.entyties.Blockchain;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * AddBlock_new request.
 *
 * payload:
 *  - userLogin
 *  - blockchainId
 *  - globalBlockNumber
 *  - prevGlobalHashHex (может быть "" для нулевого)
 *  - line (0..7)
 *  - lineBlockNumber
 *  - blockBase64 (FULL bytes блока)
 */
public class Net_AddBlock_new_Request extends Net_Request {

    private String userLogin;

    private long blockchainId;
    private int globalBlockNumber;
    private String prevGlobalHashHex;

    private short line;
    private int lineBlockNumber;

    private String blockBase64;

    public String getUserLogin() { return userLogin; }
    public void setUserLogin(String userLogin) { this.userLogin = userLogin; }

    public long getBlockchainId() { return blockchainId; }
    public void setBlockchainId(long blockchainId) { this.blockchainId = blockchainId; }

    public int getGlobalBlockNumber() { return globalBlockNumber; }
    public void setGlobalBlockNumber(int globalBlockNumber) { this.globalBlockNumber = globalBlockNumber; }

    public String getPrevGlobalHashHex() { return prevGlobalHashHex; }
    public void setPrevGlobalHashHex(String prevGlobalHashHex) { this.prevGlobalHashHex = prevGlobalHashHex; }

    public short getLine() { return line; }
    public void setLine(short line) { this.line = line; }

    public int getLineBlockNumber() { return lineBlockNumber; }
    public void setLineBlockNumber(int lineBlockNumber) { this.lineBlockNumber = lineBlockNumber; }

    public String getBlockBase64() { return blockBase64; }
    public void setBlockBase64(String blockBase64) { this.blockBase64 = blockBase64; }
}