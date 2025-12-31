package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Шаг 1 авторизации: запрос выдачи одноразового nonce (authNonce).
 *
 * Клиент по логину просит сервер сгенерировать случайный authNonce,
 * который будет использован на втором шаге при подписи.
 *
 * Формат входящего JSON:
 * {
 *   "op": "AuthChallenge",
 *   "requestId": "...",
 *   "payload": {
 *     "login": "someLogin"
 *   }
 * }
 *
 * Формат успешного ответа:
 * {
 *   "op": "AuthChallenge",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "authNonce": "base64-строка-от-32-байт"
 *   }
 * }
 */
public class Net_AuthChallenge_Request extends Net_Request {

    /**
     * Логин пользователя, для которого запускается авторизация.
     */
    private String login;

    public String getLogin() {
        return login;
    }
    public void setLogin(String login) {
        this.login = login;
    }
}