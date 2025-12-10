package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.ActiveSession;

import java.sql.*;

/**
 * DAO для таблицы active_sessions.
 *
 * Здесь мы храним данные об активных сессиях пользователя (для wss-соединений).
 *
 * Структура таблицы:
 *
 * CREATE TABLE active_sessions (
 *     sessionId              TEXT    NOT NULL PRIMARY KEY,
 *     loginId                INTEGER NOT NULL,
 *     sessionPwd             TEXT    NOT NULL,
 *     storagePwd             TEXT    NOT NULL,
 *     sessionCreatedAtMs     INTEGER NOT NULL,
 *     lastAuthirificatedAtMs INTEGER NOT NULL,
 *     pushEndpoint           TEXT,
 *     pushP256dhKey          TEXT,
 *     pushAuthKey            TEXT,
 *     clientIp               TEXT,
 *     clientInfoFromClient   TEXT,
 *     clientInfoFromRequest  TEXT,
 *     userLanguage           TEXT,
 *     FOREIGN KEY (loginId) REFERENCES solana_users(loginId)
 * );
 */
public final class ActiveSessionsDAO {

    private static volatile ActiveSessionsDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private ActiveSessionsDAO() {
    }

    public static ActiveSessionsDAO getInstance() {
        if (instance == null) {
            synchronized (ActiveSessionsDAO.class) {
                if (instance == null) {
                    instance = new ActiveSessionsDAO();
                }
            }
        }
        return instance;
    }

    /**
     * Вставка новой сессии.
     */
    public void insert(ActiveSession session) throws SQLException {
        String sql = """
            INSERT INTO active_sessions (
                sessionId,
                loginId,
                sessionPwd,
                storagePwd,
                sessionCreatedAtMs,
                lastAuthirificatedAtMs,
                pushEndpoint,
                pushP256dhKey,
                pushAuthKey,
                clientIp,
                clientInfoFromClient,
                clientInfoFromRequest,
                userLanguage
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1,  session.getSessionId());
            ps.setLong(2,    session.getLoginId());
            ps.setString(3,  session.getSessionPwd());
            ps.setString(4,  session.getStoragePwd());
            ps.setLong(5,    session.getSessionCreatedAtMs());
            ps.setLong(6,    session.getLastAuthirificatedAtMs());
            ps.setString(7,  session.getPushEndpoint());
            ps.setString(8,  session.getPushP256dhKey());
            ps.setString(9,  session.getPushAuthKey());
            ps.setString(10, session.getClientIp());
            ps.setString(11, session.getClientInfoFromClient());
            ps.setString(12, session.getClientInfoFromRequest());
            ps.setString(13, session.getUserLanguage());

            ps.executeUpdate();
        }
    }

    /**
     * Получить сессию по sessionId.
     */
    public ActiveSession getBySessionId(String sessionId) throws SQLException {
        String sql = """
            SELECT
                sessionId,
                loginId,
                sessionPwd,
                storagePwd,
                sessionCreatedAtMs,
                lastAuthirificatedAtMs,
                pushEndpoint,
                pushP256dhKey,
                pushAuthKey,
                clientIp,
                clientInfoFromClient,
                clientInfoFromRequest,
                userLanguage
            FROM active_sessions
            WHERE sessionId = ?
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRow(rs);
            }
        }
    }

    /**
     * Обновить только lastAuthirificatedAtMs для конкретной сессии.
     * (оставляю для совместимости, вдруг ещё где-то используется)
     */
    public void updateLastAuthirificatedAtMs(String sessionId, long lastAuthMs) throws SQLException {
        String sql = """
            UPDATE active_sessions
            SET lastAuthirificatedAtMs = ?
            WHERE sessionId = ?
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, lastAuthMs);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        }
    }

    /**
     * Обновление метаданных при RefreshSession:
     *  - lastAuthirificatedAtMs
     *  - clientIp
     *  - clientInfoFromClient
     *  - clientInfoFromRequest
     *  - userLanguage
     */
    public void updateOnRefresh(
            String sessionId,
            long lastAuthMs,
            String clientIp,
            String clientInfoFromClient,
            String clientInfoFromRequest,
            String userLanguage
    ) throws SQLException {

        String sql = """
            UPDATE active_sessions
            SET
                lastAuthirificatedAtMs = ?,
                clientIp               = ?,
                clientInfoFromClient   = ?,
                clientInfoFromRequest  = ?,
                userLanguage           = ?
            WHERE sessionId = ?
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, lastAuthMs);
            ps.setString(2, clientIp);
            ps.setString(3, clientInfoFromClient);
            ps.setString(4, clientInfoFromRequest);
            ps.setString(5, userLanguage);
            ps.setString(6, sessionId);
            ps.executeUpdate();
        }
    }

    /**
     * Удаление записи по sessionId.
     * Если записи нет — просто ничего не удалит (0 строк).
     */
    public void deleteBySessionId(String sessionId) throws SQLException {
        String sql = "DELETE FROM active_sessions WHERE sessionId = ?";

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        }
    }

    /**
     * Маппинг ResultSet → ActiveSession (все 13 полей).
     */
    private ActiveSession mapRow(ResultSet rs) throws SQLException {
        String sessionId              = rs.getString("sessionId");
        long   loginId                = rs.getLong("loginId");
        String sessionPwd             = rs.getString("sessionPwd");
        String storagePwd             = rs.getString("storagePwd");
        long   sessionCreatedAtMs     = rs.getLong("sessionCreatedAtMs");
        long   lastAuthirificatedAtMs = rs.getLong("lastAuthirificatedAtMs");
        String pushEndpoint           = rs.getString("pushEndpoint");
        String pushP256dhKey          = rs.getString("pushP256dhKey");
        String pushAuthKey            = rs.getString("pushAuthKey");
        String clientIp               = rs.getString("clientIp");
        String clientInfoFromClient   = rs.getString("clientInfoFromClient");
        String clientInfoFromRequest  = rs.getString("clientInfoFromRequest");
        String userLanguage           = rs.getString("userLanguage");

        return new ActiveSession(
                sessionId,
                loginId,
                sessionPwd,
                storagePwd,
                sessionCreatedAtMs,
                lastAuthirificatedAtMs,
                pushEndpoint,
                pushP256dhKey,
                pushAuthKey,
                clientIp,
                clientInfoFromClient,
                clientInfoFromRequest,
                userLanguage
        );
    }
}