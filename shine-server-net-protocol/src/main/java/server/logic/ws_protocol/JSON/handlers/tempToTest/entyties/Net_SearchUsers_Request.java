package server.logic.ws_protocol.JSON.handlers.tempToTest.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос SearchUsers — поиск логинов по префиксу.
 *
 * Клиент отправляет:
 * {
 *   "op": "SearchUsers",
 *   "requestId": "su-1",
 *   "payload": { "prefix": "any" }
 * }
 *
 * Поиск по prefix выполняется без учёта регистра.
 * В ответе возвращаем логины с тем регистром, как в БД.
 */
public class Net_SearchUsers_Request extends Net_Request {

    private String prefix;

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
}