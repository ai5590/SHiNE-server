package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.ActiveSessionEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO для таблицы active_sessions.
 *
 * Правило:
 * - методы с Connection НЕ закрывают соединение
 * - методы без Connection сами открывают и закрывают соединение
 */
public final class ActiveSessionsDAO {

    private static volatile ActiveSessionsDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private ActiveSessionsDAO() { }

    public static ActiveSessionsDAO getInstance() {
        if (instance == null) {
            synchronized (ActiveSessionsDAO.class) {
                if (instance == null) instance = new ActiveSessionsDAO();
            }
        }
        return instance;
    }

    // -------------------- INSERT --------------------

    public void insert(Connection c, ActiveSessionEntry session) throws SQLException {
        String sql = """
            INSERT INTO active_sessions (
                session_id,
                login,
                session_key,
                storage_pwd,
                session_created_at_ms,
                last_authirificated_at_ms,
                push_endpoint,
                push_p256dh_key,
                push_auth_key,
                client_ip,
                client_info_from_client,
                client_info_from_request,
                user_language
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,  session.getSessionId());
            ps.setString(2,  session.getLogin());
            ps.setString(3,  session.getSessionKey());
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

    public void insert(ActiveSessionEntry session) throws SQLException {
        try (Connection c = db.getConnection()) {
            insert(c, session);
        }
    }

    // -------------------- SELECT --------------------

    public ActiveSessionEntry getBySessionId(Connection c, String sessionId) throws SQLException {
        String sql = """
            SELECT
                session_id,
                login,
                session_key,
                storage_pwd,
                session_created_at_ms,
                last_authirificated_at_ms,
                push_endpoint,
                push_p256dh_key,
                push_auth_key,
                client_ip,
                client_info_from_client,
                client_info_from_request,
                user_language
            FROM active_sessions
            WHERE session_id = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    public ActiveSessionEntry getBySessionId(String sessionId) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getBySessionId(c, sessionId);
        }
    }

    public List<ActiveSessionEntry> getByLogin(Connection c, String login) throws SQLException {
        String sql = """
            SELECT
                session_id,
                login,
                session_key,
                storage_pwd,
                session_created_at_ms,
                last_authirificated_at_ms,
                push_endpoint,
                push_p256dh_key,
                push_auth_key,
                client_ip,
                client_info_from_client,
                client_info_from_request,
                user_language
            FROM active_sessions
            WHERE login = ? COLLATE NOCASE
            """;

        List<ActiveSessionEntry> result = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }

        return result;
    }

    public List<ActiveSessionEntry> getByLogin(String login) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByLogin(c, login);
        }
    }

    // -------------------- UPDATE --------------------

    public void updateLastAuthirificatedAtMs(Connection c, String sessionId, long lastAuthMs) throws SQLException {
        String sql = """
            UPDATE active_sessions
            SET last_authirificated_at_ms = ?
            WHERE session_id = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, lastAuthMs);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        }
    }

    public void updateLastAuthirificatedAtMs(String sessionId, long lastAuthMs) throws SQLException {
        try (Connection c = db.getConnection()) {
            updateLastAuthirificatedAtMs(c, sessionId, lastAuthMs);
        }
    }

    public void updateOnRefresh(
            Connection c,
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
                last_authirificated_at_ms = ?,
                client_ip                = ?,
                client_info_from_client  = ?,
                client_info_from_request = ?,
                user_language            = ?
            WHERE session_id = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, lastAuthMs);
            ps.setString(2, clientIp);
            ps.setString(3, clientInfoFromClient);
            ps.setString(4, clientInfoFromRequest);
            ps.setString(5, userLanguage);
            ps.setString(6, sessionId);
            ps.executeUpdate();
        }
    }

    public void updateOnRefresh(
            String sessionId,
            long lastAuthMs,
            String clientIp,
            String clientInfoFromClient,
            String clientInfoFromRequest,
            String userLanguage
    ) throws SQLException {
        try (Connection c = db.getConnection()) {
            updateOnRefresh(c, sessionId, lastAuthMs, clientIp, clientInfoFromClient, clientInfoFromRequest, userLanguage);
        }
    }

    public void updatePushSubscription(String sessionId, String endpoint, String p256dhKey, String authKey) throws SQLException {
        try (Connection c = db.getConnection()) {
            String sql = """
                UPDATE active_sessions
                SET push_endpoint = ?,
                    push_p256dh_key = ?,
                    push_auth_key = ?
                WHERE session_id = ?
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, endpoint);
                ps.setString(2, p256dhKey);
                ps.setString(3, authKey);
                ps.setString(4, sessionId);
                ps.executeUpdate();
            }
        }
    }

    // -------------------- DELETE --------------------

    public void deleteBySessionId(Connection c, String sessionId) throws SQLException {
        String sql = "DELETE FROM active_sessions WHERE session_id = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        }
    }

    public void deleteBySessionId(String sessionId) throws SQLException {
        try (Connection c = db.getConnection()) {
            deleteBySessionId(c, sessionId);
        }
    }

    // -------------------- MAPPER --------------------

    private ActiveSessionEntry mapRow(ResultSet rs) throws SQLException {
        String sessionId              = rs.getString("session_id");
        String login                  = rs.getString("login");
        String sessionKey             = rs.getString("session_key");
        String storagePwd             = rs.getString("storage_pwd");
        long   sessionCreatedAtMs     = rs.getLong("session_created_at_ms");
        long   lastAuthirificatedAtMs = rs.getLong("last_authirificated_at_ms");
        String pushEndpoint           = rs.getString("push_endpoint");
        String pushP256dhKey          = rs.getString("push_p256dh_key");
        String pushAuthKey            = rs.getString("push_auth_key");
        String clientIp               = rs.getString("client_ip");
        String clientInfoFromClient   = rs.getString("client_info_from_client");
        String clientInfoFromRequest  = rs.getString("client_info_from_request");
        String userLanguage           = rs.getString("user_language");

        return new ActiveSessionEntry(
                sessionId,
                login,
                sessionKey,
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
