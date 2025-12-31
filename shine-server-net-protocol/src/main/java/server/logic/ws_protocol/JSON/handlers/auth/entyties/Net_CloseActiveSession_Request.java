package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос CloseActiveSession — закрытие активной сессии пользователя.
 *
 * Допустимые режимы:
 *
 * 1) Пользователь уже авторизован (AUTH_STATUS_USER):
 *    - поле sessionId:
 *        * если заполнено — закрывается указанная сессия пользователя;
 *        * если пустое — закрывается текущая авторизованная сессия
 *          (та, в рамках которой выполняется запрос).
 *    - поля timeMs и signatureB64 могут быть пустыми и игнорируются.
 *
 * 2) Пользователь в статусе AUTH_STATUS_AUTH_IN_PROGRESS:
 *    - требуется дополнительно подтвердить владение ключом:
 *       * timeMs — время на клиенте (мс с 1970-01-01),
 *       * signatureB64 — подпись Ed25519 над строкой
 *         "AUTHORIFICATED:" + timeMs + authNonce.
 *    - authNonce берётся из шага 1 (AuthChallenge) и хранится в ctx.authNonce.
 *    - если подпись корректна, сервер закрывает указанную sessionId (или текущую,
 *      если sessionId не задана) и рвёт соответствующее WebSocket-подключение.
 */
public class Net_CloseActiveSession_Request extends Net_Request {

    /** Идентификатор сессии, которую нужно закрыть. Может быть пустым. */
    private String sessionId;

    /** Время на стороне клиента (мс с 1970-01-01). Используется при AUTH_IN_PROGRESS. */
    private long timeMs;

    /** Подпись Ed25519 над строкой "AUTHORIFICATED:" + timeMs + authNonce (base64). */
    private String signatureB64;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

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