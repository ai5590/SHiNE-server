package server.logic.ws_protocol.JSON.entyties.tempToTest;

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
 *     "loginKey": "base64-ed25519-public-key-login",
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
    private String loginKey;
    private String deviceKey;
    private Integer bchLimit;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getBlockchainName() { return blockchainName; }
    public void setBlockchainName(String blockchainName) { this.blockchainName = blockchainName; }

    public String getLoginKey() { return loginKey; }
    public void setLoginKey(String loginKey) { this.loginKey = loginKey; }

    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }

    public Integer getBchLimit() { return bchLimit; }
    public void setBchLimit(Integer bchLimit) { this.bchLimit = bchLimit; }
}