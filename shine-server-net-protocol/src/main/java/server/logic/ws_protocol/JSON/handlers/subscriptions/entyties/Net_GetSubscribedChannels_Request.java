package server.logic.ws_protocol.JSON.handlers.subscriptions.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос GetSubscribedChannels.
 *
 * Клиент отправляет:
 * {
 *   "op": "GetSubscribedChannels",
 *   "requestId": "....",
 *   "payload": {
 *     "login": "anya"
 *   }
 * }
 */
public class Net_GetSubscribedChannels_Request extends Net_Request {

    private String login;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
}