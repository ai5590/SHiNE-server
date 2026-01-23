package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Шаг 2 входа в существующую сессию (v2):
 * SessionLogin(sessionId, timeMs, signatureB64) -> storagePwd, AUTH_STATUS_USER
 *
 * Подпись делается sessionKey (приватный ключ на устройстве) над строкой (UTF-8):
 *   SESSION_LOGIN:{sessionId}:{timeMs}:{nonce}
 *
 * nonce берётся из SessionChallenge и хранится в ctx (одноразовый, TTL).
 */
public class Net_SessionLogin_Request extends Net_Request {

    private String sessionId;
    private long timeMs;
    private String signatureB64;

    /** Краткая строка от клиента (до 50 символов) с описанием устройства/клиента. */
    private String clientInfo;

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

    public String getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(String clientInfo) {
        this.clientInfo = clientInfo;
    }
}