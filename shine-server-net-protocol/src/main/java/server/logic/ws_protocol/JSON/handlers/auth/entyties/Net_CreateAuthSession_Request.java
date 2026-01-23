package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Шаг 2 (v2): создание новой сессии ТОЛЬКО через deviceKey.
 *
 * Шаги:
 *  1) AuthChallenge(login) -> authNonce
 *  2) CreateAuthSession(storagePwd, sessionPubKeyB64, timeMs, signatureB64, clientInfo)
 *
 * Подпись deviceKey делается над строкой (UTF-8):
 *   AUTH_CREATE_SESSION:{login}:{timeMs}:{authNonce}:{sessionPubKeyB64}:{storagePwd}
 *
 * Важно:
 * - sessionKey генерируется на клиенте, на сервер отправляется ТОЛЬКО sessionPubKeyB64 (32 bytes base64).
 * - В БД active_sessions.session_key хранится sessionPubKeyB64.
 */
public class Net_CreateAuthSession_Request extends Net_Request {

    /** Клиентский пароль для хранения данных (base64url от 32 байт). */
    private String storagePwd;

    /** Публичный ключ сессии (sessionPubKey), base64 от 32 байт. */
    private String sessionPubKeyB64;

    /** Время на стороне клиента (мс с 1970-01-01). */
    private long timeMs;

    /** Подпись Ed25519(deviceKey) над строкой AUTH_CREATE_SESSION:... (base64). */
    private String signatureB64;

    /** Краткая строка от клиента (до 50 символов) с описанием устройства/клиента. */
    private String clientInfo;

    public String getStoragePwd() {
        return storagePwd;
    }

    public void setStoragePwd(String storagePwd) {
        this.storagePwd = storagePwd;
    }

    public String getSessionPubKeyB64() {
        return sessionPubKeyB64;
    }

    public void setSessionPubKeyB64(String sessionPubKeyB64) {
        this.sessionPubKeyB64 = sessionPubKeyB64;
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