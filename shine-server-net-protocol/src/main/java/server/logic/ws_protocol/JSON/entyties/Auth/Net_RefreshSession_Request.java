package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetRequest;

/**
 * Запрос RefreshSession.
 *
 * Используется для повторного входа без повторной подписи:
 * клиент хранит sessionId и sessionPwd, которые получил на шаге 2.
 *
 * JSON (payload):
 * {
 *   "sessionId": "base64-id-сессии",
 *   "sessionPwd": "base64-sessionPwd",
 *   "clientInfo": "до 50 символов, краткая строка об устройстве"
 * }
 */
public class Net_RefreshSession_Request extends NetRequest {

    private String sessionId;
    private String sessionPwd;

    /**
     * Краткая строка с информацией об устройстве/клиенте, до 50 символов.
     * Например: "PWA/Chrome/Android".
     */
    private String clientInfo;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionPwd() {
        return sessionPwd;
    }

    public void setSessionPwd(String sessionPwd) {
        this.sessionPwd = sessionPwd;
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(String clientInfo) {
        this.clientInfo = clientInfo;
    }
}