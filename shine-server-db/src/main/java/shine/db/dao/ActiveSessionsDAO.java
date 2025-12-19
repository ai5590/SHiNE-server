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

    /** Вставка с внешним соединением. Соединение НЕ закрывает. */
    public void insert(Connection c, ActiveSessionEntry session) throws SQLException {
        String sql = """
            INSERT INTO active_sessions (
                sessionId,
                login,
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

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,  session.getSessionId());
            ps.setString(2,  session.getLogin());
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

    /** Вставка без внешнего соединения. Сам открывает/закрывает. */
    public void insert(ActiveSessionEntry session) throws SQLException {
        try (Connection c = db.getConnection()) {
            insert(c, session);
        }
    }

    // -------------------- SELECT --------------------

    /** Получить по sessionId с внешним соединением. Соединение НЕ закрывает. */
    public ActiveSessionEntry getBySessionId(Connection c, String sessionId) throws SQLException {
        String sql = """
            SELECT
                sessionId,
                login,
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

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /** Получить по sessionId без внешнего соединения. Сам открывает/закрывает. */
    public ActiveSessionEntry getBySessionId(String sessionId) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getBySessionId(c, sessionId);
        }
    }

    /** Получить список по login с внешним соединением. Соединение НЕ закрывает. */
    public List<ActiveSessionEntry> getByLogin(Connection c, String login) throws SQLException {
        String sql = """
            SELECT
                sessionId,
                login,
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
            WHERE login = ?
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

    /** Получить список по login без внешнего соединения. Сам открывает/закрывает. */
    public List<ActiveSessionEntry> getByLogin(String login) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByLogin(c, login);
        }
    }

    // -------------------- UPDATE --------------------

    /** Обновить lastAuthirificatedAtMs с внешним соединением. Соединение НЕ закрывает. */
    public void updateLastAuthirificatedAtMs(Connection c, String sessionId, long lastAuthMs) throws SQLException {
        String sql = """
            UPDATE active_sessions
            SET lastAuthirificatedAtMs = ?
            WHERE sessionId = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, lastAuthMs);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        }
    }

    /** Обновить lastAuthirificatedAtMs без внешнего соединения. Сам открывает/закрывает. */
    public void updateLastAuthirificatedAtMs(String sessionId, long lastAuthMs) throws SQLException {
        try (Connection c = db.getConnection()) {
            updateLastAuthirificatedAtMs(c, sessionId, lastAuthMs);
        }
    }

    /** Обновить данные refresh с внешним соединением. Соединение НЕ закрывает. */
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
                lastAuthirificatedAtMs = ?,
                clientIp               = ?,
                clientInfoFromClient   = ?,
                clientInfoFromRequest  = ?,
                userLanguage           = ?
            WHERE sessionId = ?
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

    /** Обновить данные refresh без внешнего соединения. Сам открывает/закрывает. */
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

    // -------------------- DELETE --------------------

    /** Удалить по sessionId с внешним соединением. Соединение НЕ закрывает. */
    public void deleteBySessionId(Connection c, String sessionId) throws SQLException {
        String sql = "DELETE FROM active_sessions WHERE sessionId = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        }
    }

    /** Удалить по sessionId без внешнего соединения. Сам открывает/закрывает. */
    public void deleteBySessionId(String sessionId) throws SQLException {
        try (Connection c = db.getConnection()) {
            deleteBySessionId(c, sessionId);
        }
    }

    // -------------------- MAPPER --------------------

    private ActiveSessionEntry mapRow(ResultSet rs) throws SQLException {
        String sessionId              = rs.getString("sessionId");
        String login                  = rs.getString("login");
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
                login,
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