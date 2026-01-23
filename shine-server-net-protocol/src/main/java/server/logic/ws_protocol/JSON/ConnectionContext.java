package server.logic.ws_protocol.JSON;

import org.eclipse.jetty.websocket.api.Session;
import shine.db.entities.SolanaUserEntry;
import shine.db.entities.ActiveSessionEntry;

/**
 * ConnectionContext — контекст состояния одного WebSocket-соединения.
 * Живёт ровно столько же, сколько живёт подключение.
 *
 * Важно (v2):
 * - Авторизация всегда 2 шага:
 *   A) Создание новой сессии через deviceKey:
 *      AuthChallenge(login) -> ctx.authNonce
 *      CreateAuthSession(...) -> ctx.AUTH_STATUS_USER + ctx.activeSession
 *
 *   B) Вход в существующую сессию через sessionKey:
 *      SessionChallenge(sessionId) -> ctx.sessionLoginNonce + ctx.sessionLoginSessionId + expiresAt
 *      SessionLogin(...) -> проверка подписи sessionKey по pubkey из БД -> ctx.AUTH_STATUS_USER
 */
public class ConnectionContext {

    // Статусы аутентификации
    public static final int AUTH_STATUS_NONE = 0;              // анонимный / не авторизован
    public static final int AUTH_STATUS_AUTH_IN_PROGRESS = 1;  // выполнен challenge (AuthChallenge или SessionChallenge)
    public static final int AUTH_STATUS_USER = 2;              // авторизованный пользователь

    // Полный пользователь из БД (solana_users)
    private SolanaUserEntry solanaUserEntry;

    // Активная сессия из БД (active_sessions)
    private ActiveSessionEntry activeSessionEntry;

    /**
     * Идентификатор сессии — base64-строка от 32 байт.
     * Заполняется после успешного входа (AUTH_STATUS_USER).
     */
    private String sessionId;

    /**
     * Одноразовый nonce, выданный на шаге 1 (AuthChallenge),
     * используется на шаге CreateAuthSession для проверки подписи deviceKey.
     */
    private String authNonce;

    /* ===================== SessionLogin challenge (v2) ===================== */

    /**
     * Одноразовый nonce, выданный на шаге SessionChallenge(sessionId),
     * используется на шаге SessionLogin для проверки подписи sessionKey.
     */
    private String sessionLoginNonce;

    /**
     * sessionId, для которого был выдан sessionLoginNonce.
     * Нужен, чтобы SessionLogin не мог "подставить" другой sessionId.
     */
    private String sessionLoginSessionId;

    /**
     * Время истечения sessionLoginNonce (мс с 1970-01-01).
     * Если текущее время > expiresAt, то nonce считается недействительным.
     */
    private long sessionLoginNonceExpiresAtMs;

    /* ====================================================================== */

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

    public SolanaUserEntry getSolanaUser() {
        return solanaUserEntry;
    }

    public void setSolanaUser(SolanaUserEntry solanaUserEntry) {
        this.solanaUserEntry = solanaUserEntry;
    }

    public ActiveSessionEntry getActiveSession() {
        return activeSessionEntry;
    }

    public void setActiveSession(ActiveSessionEntry activeSessionEntry) {
        this.activeSessionEntry = activeSessionEntry;
    }

    // --- Удобный геттер для логина ---

    public String getLogin() {
        return solanaUserEntry != null ? solanaUserEntry.getLogin() : null;
    }

    // --- sessionId ---

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    // --- authNonce ---

    public String getAuthNonce() {
        return authNonce;
    }

    public void setAuthNonce(String authNonce) {
        this.authNonce = authNonce;
    }

    // --- sessionLoginNonce (v2) ---

    public String getSessionLoginNonce() {
        return sessionLoginNonce;
    }

    public void setSessionLoginNonce(String sessionLoginNonce) {
        this.sessionLoginNonce = sessionLoginNonce;
    }

    public String getSessionLoginSessionId() {
        return sessionLoginSessionId;
    }

    public void setSessionLoginSessionId(String sessionLoginSessionId) {
        this.sessionLoginSessionId = sessionLoginSessionId;
    }

    public long getSessionLoginNonceExpiresAtMs() {
        return sessionLoginNonceExpiresAtMs;
    }

    public void setSessionLoginNonceExpiresAtMs(long sessionLoginNonceExpiresAtMs) {
        this.sessionLoginNonceExpiresAtMs = sessionLoginNonceExpiresAtMs;
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
        solanaUserEntry = null;
        activeSessionEntry = null;

        sessionId = null;
        authNonce = null;

        sessionLoginNonce = null;
        sessionLoginSessionId = null;
        sessionLoginNonceExpiresAtMs = 0;

        authenticationStatus = AUTH_STATUS_NONE;
        wsSession = null;
    }

    @Override
    public String toString() {
        return "ConnectionContext{" +
                "login='" + getLogin() + '\'' +
                ", sessionId=" + sessionId +
                ", authenticationStatus=" + authenticationStatus +
                '}';
    }
}