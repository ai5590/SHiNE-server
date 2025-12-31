package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Ответ на AuthChallenge.
 *
 * При успехе сервер возвращает одноразовый nonce для подписи (authNonce),
 * который клиент обязан использовать на втором шаге при формировании строки
 * для цифровой подписи.
 *
 * JSON:
 * {
 *   "op": "AuthChallenge",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "authNonce": "base64-строка-от-32-байт"
 *   }
 * }
 */
public class Net_AuthChallenge_Response extends Net_Response {

    /**
     * Одноразовый nonce для авторификации.
     * Строка — это base64-представление 32 случайных байт.
     */
    private String authNonce;

    public String getAuthNonce() {
        return authNonce;
    }

    public void setAuthNonce(String authNonce) {
        this.authNonce = authNonce;
    }
}