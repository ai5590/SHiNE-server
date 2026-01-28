package server.logic.ws_protocol.JSON.handlers.tempToTest.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос GetUser — проверка/получение пользователя по login.
 *
 * Клиент отправляет:
 *
 * {
 *   "op": "GetUser",
 *   "requestId": "u-1",
 *   "payload": {
 *     "login": "AnYa"
 *   }
 * }
 *
 * Поиск по login выполняется без учёта регистра.
 * В ответе возвращаем login/blockchainName с тем регистром, как в БД.
 */
public class Net_GetUser_Request extends Net_Request {

    private String login;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
}