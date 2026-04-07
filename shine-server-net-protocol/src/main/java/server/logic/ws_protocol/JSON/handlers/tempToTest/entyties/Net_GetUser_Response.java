package server.logic.ws_protocol.JSON.handlers.tempToTest.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Ответ GetUser.
 *
 * Всегда status=200.
 *
 * Пример (нет пользователя):
 * {
 *   "op": "GetUser",
 *   "requestId": "u-1",
 *   "status": 200,
 *   "payload": { "exists": false }
 * }
 *
 * Пример (есть пользователь):
 * {
 *   "op": "GetUser",
 *   "requestId": "u-1",
 *   "status": 200,
 *   "payload": {
 *     "exists": true,
 *     "login": "Anya",
 *     "blockchainName": "anya-001",
 *     "solanaKey": "...",
 *     "blockchainKey": "...",
 *     "deviceKey": "..."
 *   }
 * }
 */
public class Net_GetUser_Response extends Net_Response {

    private Boolean exists;

    private String login;
    private String blockchainName;
    private String solanaKey;
    private String blockchainKey;
    private String deviceKey;
    private Integer serverLastGlobalNumber;
    private String serverLastGlobalHash;

    public Boolean getExists() { return exists; }
    public void setExists(Boolean exists) { this.exists = exists; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getBlockchainName() { return blockchainName; }
    public void setBlockchainName(String blockchainName) { this.blockchainName = blockchainName; }

    public String getSolanaKey() { return solanaKey; }
    public void setSolanaKey(String solanaKey) { this.solanaKey = solanaKey; }

    public String getBlockchainKey() { return blockchainKey; }
    public void setBlockchainKey(String blockchainKey) { this.blockchainKey = blockchainKey; }

    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }

    public Integer getServerLastGlobalNumber() { return serverLastGlobalNumber; }
    public void setServerLastGlobalNumber(Integer serverLastGlobalNumber) { this.serverLastGlobalNumber = serverLastGlobalNumber; }

    public String getServerLastGlobalHash() { return serverLastGlobalHash; }
    public void setServerLastGlobalHash(String serverLastGlobalHash) { this.serverLastGlobalHash = serverLastGlobalHash; }
}
