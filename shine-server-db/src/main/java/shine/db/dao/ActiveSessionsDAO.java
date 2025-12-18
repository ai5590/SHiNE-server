package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.ActiveSessionEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO для таблицы active_sessions.
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

    public void insert(ActiveSessionEntry session) throws SQLException {
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

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

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

    public ActiveSessionEntry getBySessionId(String sessionId) throws SQLException {
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

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, sessionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    public List<ActiveSessionEntry> getByLoginId(long loginId) throws SQLException {
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
            WHERE loginId = ?
            """;

        List<ActiveSessionEntry> result = new ArrayList<>();

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, loginId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }

        return result;
    }

    public void updateLastAuthirificatedAtMs(String sessionId, long lastAuthMs) throws SQLException {
        String sql = """
            UPDATE active_sessions
            SET lastAuthirificatedAtMs = ?
            WHERE sessionId = ?
            """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, lastAuthMs);
            ps.setString(2, sessionId);
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

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, lastAuthMs);
            ps.setString(2, clientIp);
            ps.setString(3, clientInfoFromClient);
            ps.setString(4, clientInfoFromRequest);
            ps.setString(5, userLanguage);
            ps.setString(6, sessionId);
            ps.executeUpdate();
        }
    }

    public void deleteBySessionId(String sessionId) throws SQLException {
        String sql = "DELETE FROM active_sessions WHERE sessionId = ?";

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            ps.executeUpdate();
        }
    }

    private ActiveSessionEntry mapRow(ResultSet rs) throws SQLException {
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

        return new ActiveSessionEntry(
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