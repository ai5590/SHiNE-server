package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос ListSessions — список активных сессий пользователя.
 *
 * Режимы безопасности такие же, как у CloseActiveSession:
 *
 * 1) Пользователь уже авторизован (AUTH_STATUS_USER):
 *    - поля timeMs и signatureB64 могут быть пустыми и игнорируются.
 *
 * 2) Пользователь в статусе AUTH_STATUS_AUTH_IN_PROGRESS:
 *    - требуется подпись Ed25519 над строкой
 *      "AUTHORIFICATED:" + timeMs + authNonce
 *      (authNonce сохранён в ctx.authNonce после AuthChallenge).
 *
 * 3) Анонимный клиент (AUTH_STATUS_NONE или нет пользователя в ctx):
 *    - возвращается ошибка NOT_AUTHENTICATED.
 *
 * JSON:
 * {
 *   "op": "ListSessions",
 *   "requestId": "...",
 *   "payload": {
 *     "timeMs": 1733310000000,           // при AUTH_IN_PROGRESS
 *     "signatureB64": "base64-подпись"   // при AUTH_IN_PROGRESS
 *   }
 * }
 */
public class Net_ListSessions_Request extends Net_Request {

    /** Время на стороне клиента (мс с 1970-01-01). Используется при AUTH_IN_PROGRESS. */
    private long timeMs;

    /** Подпись Ed25519 над строкой "AUTHORIFICATED:" + timeMs + authNonce (base64). */
    private String signatureB64;

    public long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(long timeMs) {
        this.timeMs = timeMs;
    }

    public String getSignatureB64() {
        return signatureB64;
    }

    public void setSignatureB64(String signatureB64) {
        this.signatureB64 = signatureB64;
    }
}