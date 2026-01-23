package shine.db.entities;

/**
 * Модель активной сессии (таблица active_sessions).
 */
public class ActiveSessionEntry {

    private String sessionId;
    private String login;

    /** session_key: публичный ключ сессии (base64 от 32 байт). */
    private String sessionKey;

    private String storagePwd;
    private long   sessionCreatedAtMs;
    private long   lastAuthirificatedAtMs;

    private String pushEndpoint;
    private String pushP256dhKey;
    private String pushAuthKey;

    private String clientIp;
    private String clientInfoFromClient;
    private String clientInfoFromRequest;
    private String userLanguage;

    public ActiveSessionEntry() { }

    public ActiveSessionEntry(String sessionId,
                              String login,
                              String sessionKey,
                              String storagePwd,
                              long sessionCreatedAtMs,
                              long lastAuthirificatedAtMs,
                              String pushEndpoint,
                              String pushP256dhKey,
                              String pushAuthKey,
                              String clientIp,
                              String clientInfoFromClient,
                              String clientInfoFromRequest,
                              String userLanguage) {
        this.sessionId = sessionId;
        this.login = login;
        this.sessionKey = sessionKey;
        this.storagePwd = storagePwd;
        this.sessionCreatedAtMs = sessionCreatedAtMs;
        this.lastAuthirificatedAtMs = lastAuthirificatedAtMs;
        this.pushEndpoint = pushEndpoint;
        this.pushP256dhKey = pushP256dhKey;
        this.pushAuthKey = pushAuthKey;
        this.clientIp = clientIp;
        this.clientInfoFromClient = clientInfoFromClient;
        this.clientInfoFromRequest = clientInfoFromRequest;
        this.userLanguage = userLanguage;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public String getStoragePwd() { return storagePwd; }
    public void setStoragePwd(String storagePwd) { this.storagePwd = storagePwd; }

    public long getSessionCreatedAtMs() { return sessionCreatedAtMs; }
    public void setSessionCreatedAtMs(long sessionCreatedAtMs) { this.sessionCreatedAtMs = sessionCreatedAtMs; }

    public long getLastAuthirificatedAtMs() { return lastAuthirificatedAtMs; }
    public void setLastAuthirificatedAtMs(long lastAuthirificatedAtMs) { this.lastAuthirificatedAtMs = lastAuthirificatedAtMs; }

    public String getPushEndpoint() { return pushEndpoint; }
    public void setPushEndpoint(String pushEndpoint) { this.pushEndpoint = pushEndpoint; }

    public String getPushP256dhKey() { return pushP256dhKey; }
    public void setPushP256dhKey(String pushP256dhKey) { this.pushP256dhKey = pushP256dhKey; }

    public String getPushAuthKey() { return pushAuthKey; }
    public void setPushAuthKey(String pushAuthKey) { this.pushAuthKey = pushAuthKey; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getClientInfoFromClient() { return clientInfoFromClient; }
    public void setClientInfoFromClient(String clientInfoFromClient) { this.clientInfoFromClient = clientInfoFromClient; }

    public String getClientInfoFromRequest() { return clientInfoFromRequest; }
    public void setClientInfoFromRequest(String clientInfoFromRequest) { this.clientInfoFromRequest = clientInfoFromRequest; }

    public String getUserLanguage() { return userLanguage; }
    public void setUserLanguage(String userLanguage) { this.userLanguage = userLanguage; }
}