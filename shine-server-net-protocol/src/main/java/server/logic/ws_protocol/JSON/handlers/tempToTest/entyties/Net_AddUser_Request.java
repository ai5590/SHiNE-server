package server.logic.ws_protocol.JSON.handlers.tempToTest.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос AddUser — временная/тестовая регистрация локального пользователя.
 *
 * Клиент отправляет:
 *
 * {
 *   "op": "AddUser",
 *   "requestId": "test-add-1",
 *   "payload": {
 *     "login": "anya",
 *     "blockchainName": "anya0001",
 *     "solanaKey": "base64-ed25519-public-key-login",
 *     "blockchainKey": "base64-ed25519-public-key-blockchain",
 *     "deviceKey": "base64-ed25519-public-key-device",
 *     "bchLimit": 1000000
 *   }
 * }
 *
 * Все поля лежат внутри payload.
 */
public class Net_AddUser_Request extends Net_Request {

    private String login;
    private String blockchainName;

    /** Ключ пользователя Solana (публичный ключ логина) */
    private String solanaKey;

    /** Ключ блокчейна (публичный ключ блокчейна) */
    private String blockchainKey;

    /** Ключ устройства (публичный ключ устройства) */
    private String deviceKey;

    private Integer bchLimit;

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

    public Integer getBchLimit() { return bchLimit; }
    public void setBchLimit(Integer bchLimit) { this.bchLimit = bchLimit; }
}