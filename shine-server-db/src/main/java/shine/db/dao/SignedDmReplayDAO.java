package shine.db.dao;

import shine.db.SqliteDbController;

import java.sql.Connection;
import java.sql.PreparedStatement;

public final class SignedDmReplayDAO {
    private static volatile SignedDmReplayDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private SignedDmReplayDAO() {}

    public static SignedDmReplayDAO getInstance() {
        if (instance == null) {
            synchronized (SignedDmReplayDAO.class) {
                if (instance == null) instance = new SignedDmReplayDAO();
            }
        }
        return instance;
    }

    public boolean registerUnique(String fromLogin, long timeMs, long nonce, long nowMs) throws Exception {
        cleanupExpired(nowMs - 15L * 60L * 1000L);
        try (Connection c = db.getConnection()) {
            String sql = """
                INSERT OR IGNORE INTO signed_direct_message_replay (
                    from_login, time_ms, nonce, created_at_ms
                ) VALUES (?, ?, ?, ?)
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, fromLogin);
                ps.setLong(2, timeMs);
                ps.setLong(3, nonce);
                ps.setLong(4, nowMs);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public void cleanupExpired(long minCreatedAtMs) throws Exception {
        try (Connection c = db.getConnection()) {
            String sql = "DELETE FROM signed_direct_message_replay WHERE created_at_ms < ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, minCreatedAtMs);
                ps.executeUpdate();
            }
        }
    }
}
