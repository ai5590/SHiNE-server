package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.PushTokenEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class PushTokensDAO {
    private static volatile PushTokensDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private PushTokensDAO() {}

    public static PushTokensDAO getInstance() {
        if (instance == null) {
            synchronized (PushTokensDAO.class) {
                if (instance == null) instance = new PushTokensDAO();
            }
        }
        return instance;
    }

    public void upsert(PushTokenEntry entry) throws Exception {
        try (Connection c = db.getConnection()) {
            String sql = """
                INSERT INTO user_push_tokens (
                    token_id, login, session_id, provider, token, platform, user_agent, updated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(token_id) DO UPDATE SET
                    login=excluded.login,
                    session_id=excluded.session_id,
                    provider=excluded.provider,
                    token=excluded.token,
                    platform=excluded.platform,
                    user_agent=excluded.user_agent,
                    updated_at_ms=excluded.updated_at_ms
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, entry.getTokenId());
                ps.setString(2, entry.getLogin());
                ps.setString(3, entry.getSessionId());
                ps.setString(4, entry.getProvider());
                ps.setString(5, entry.getToken());
                ps.setString(6, entry.getPlatform());
                ps.setString(7, entry.getUserAgent());
                ps.setLong(8, entry.getUpdatedAtMs());
                ps.executeUpdate();
            }
        }
    }

    public List<PushTokenEntry> listByLogin(String login) throws Exception {
        try (Connection c = db.getConnection()) {
            String sql = """
                SELECT token_id, login, session_id, provider, token, platform, user_agent, updated_at_ms
                FROM user_push_tokens
                WHERE login = ?
                ORDER BY updated_at_ms DESC
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, login);
                try (ResultSet rs = ps.executeQuery()) {
                    List<PushTokenEntry> out = new ArrayList<>();
                    while (rs.next()) {
                        PushTokenEntry e = new PushTokenEntry();
                        e.setTokenId(rs.getString("token_id"));
                        e.setLogin(rs.getString("login"));
                        e.setSessionId(rs.getString("session_id"));
                        e.setProvider(rs.getString("provider"));
                        e.setToken(rs.getString("token"));
                        e.setPlatform(rs.getString("platform"));
                        e.setUserAgent(rs.getString("user_agent"));
                        e.setUpdatedAtMs(rs.getLong("updated_at_ms"));
                        out.add(e);
                    }
                    return out;
                }
            }
        }
    }

    public List<PushTokenEntry> listByLoginAndSession(String login, String sessionId) throws Exception {
        try (Connection c = db.getConnection()) {
            String sql = """
                SELECT token_id, login, session_id, provider, token, platform, user_agent, updated_at_ms
                FROM user_push_tokens
                WHERE login = ? AND session_id = ?
                ORDER BY updated_at_ms DESC
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, login);
                ps.setString(2, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<PushTokenEntry> out = new ArrayList<>();
                    while (rs.next()) {
                        PushTokenEntry e = new PushTokenEntry();
                        e.setTokenId(rs.getString("token_id"));
                        e.setLogin(rs.getString("login"));
                        e.setSessionId(rs.getString("session_id"));
                        e.setProvider(rs.getString("provider"));
                        e.setToken(rs.getString("token"));
                        e.setPlatform(rs.getString("platform"));
                        e.setUserAgent(rs.getString("user_agent"));
                        e.setUpdatedAtMs(rs.getLong("updated_at_ms"));
                        out.add(e);
                    }
                    return out;
                }
            }
        }
    }
}
