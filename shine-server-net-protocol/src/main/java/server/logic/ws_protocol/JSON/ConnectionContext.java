package server.logic.ws_protocol.JSON;

import org.eclipse.jetty.websocket.api.Session;
import shine.db.entities.SolanaUser;
import shine.db.entities.ActiveSession;

/**
 * ConnectionContext — контекст состояния одного WebSocket-соединения.
 * Живёт ровно столько же, сколько живёт подключение.
 */
public class ConnectionContext {

    // Статусы аутентификации
    public static final int AUTH_STATUS_NONE = 0;              // анонимный / не авторизован
    public static final int AUTH_STATUS_AUTH_IN_PROGRESS = 1;  // получен AuthChallenge
    public static final int AUTH_STATUS_USER = 2;              // авторизованный пользователь

    // Полный пользователь из БД (solana_users)
    private SolanaUser solanaUser;

    // Активная сессия из БД (active_sessions)
    private ActiveSession activeSession;

    /**
     * Идентификатор сессии — base64-строка от 32 байт.
     */
    private String sessionId;

    /**
     * Секрет сессии (то, что хранится в active_sessions.session_pwd).
     */
    private String sessionPwd;

    /**
     * Одноразовый nonce, выданный на шаге 1 (AuthChallenge),
     * используется на шаге 2 для проверки подписи.
     */
    private String authNonce;

    /**
     * Текущий статус аутентификации.
     * См. константы AUTH_STATUS_*
     */
    private int authenticationStatus = AUTH_STATUS_NONE;

    /**
     * WebSocket-сессия Jetty для данного подключения.
     * Нужна, чтобы через ConnectionContext можно было отправлять сообщения клиенту.
     */
    private Session wsSession;

    // --- WebSocket Session ---

    public Session getWsSession() {
        return wsSession;
    }

    public void setWsSession(Session wsSession) {
        this.wsSession = wsSession;
    }

    // --- SolanaUser / ActiveSession ---

    public SolanaUser getSolanaUser() {
        return solanaUser;
    }

    public void setSolanaUser(SolanaUser solanaUser) {
        this.solanaUser = solanaUser;
    }

    public ActiveSession getActiveSession() {
        return activeSession;
    }

    public void setActiveSession(ActiveSession activeSession) {
        this.activeSession = activeSession;
    }

    // --- Удобные геттеры для логина ---

    public String getLogin() {
        return solanaUser != null ? solanaUser.getLogin() : null;
    }

    public Long getLoginId() {
        return solanaUser != null ? solanaUser.getLoginId() : null;
    }

    // --- sessionId / sessionPwd ---

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

    // --- authNonce ---

    public String getAuthNonce() {
        return authNonce;
    }

    public void setAuthNonce(String authNonce) {
        this.authNonce = authNonce;
    }

    // --- auth status ---

    public int getAuthenticationStatus() {
        return authenticationStatus;
    }

    public void setAuthenticationStatus(int authenticationStatus) {
        this.authenticationStatus = authenticationStatus;
    }

    public boolean isAuthenticatedUser() {
        return authenticationStatus == AUTH_STATUS_USER;
    }

    public boolean isAnonymous() {
        return authenticationStatus == AUTH_STATUS_NONE;
    }

    public void reset() {
        solanaUser = null;
        activeSession = null;

        sessionId = null;
        sessionPwd = null;
        authNonce = null;

        authenticationStatus = AUTH_STATUS_NONE;
        wsSession = null;
    }

    @Override
    public String toString() {
        return "ConnectionContext{" +
                "login='" + getLogin() + '\'' +
                ", loginId=" + getLoginId() +
                ", sessionId=" + sessionId +
                ", authenticationStatus=" + authenticationStatus +
                '}';
    }
}