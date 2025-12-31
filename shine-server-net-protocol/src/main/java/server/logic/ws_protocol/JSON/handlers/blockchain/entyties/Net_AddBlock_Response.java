package server.logic.ws_protocol.JSON.handlers.blockchain.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Ответ:
 * - reasonCode (null если ok)
 * - serverLastGlobalNumber / serverLastGlobalHash
 */
public final class Net_AddBlock_Response extends Net_Response {

    /** null если ok, иначе строка причины (bad_block_base64, user_not_found, и т.п.) */
    private String reasonCode;

    /** что сервер считает последним по глобальной цепочке */
    private int serverLastGlobalNumber;
    private String serverLastGlobalHash;

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public int getServerLastGlobalNumber() { return serverLastGlobalNumber; }
    public void setServerLastGlobalNumber(int v) { this.serverLastGlobalNumber = v; }

    public String getServerLastGlobalHash() { return serverLastGlobalHash; }
    public void setServerLastGlobalHash(String v) { this.serverLastGlobalHash = v; }
}