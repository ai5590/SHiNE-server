package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.SignedDirectMessageHistoryEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;

public final class SignedDirectMessagesHistoryDAO {
    private static volatile SignedDirectMessagesHistoryDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private SignedDirectMessagesHistoryDAO() {}

    public static SignedDirectMessagesHistoryDAO getInstance() {
        if (instance == null) {
            synchronized (SignedDirectMessagesHistoryDAO.class) {
                if (instance == null) instance = new SignedDirectMessagesHistoryDAO();
            }
        }
        return instance;
    }

    public void insert(SignedDirectMessageHistoryEntry e) throws Exception {
        try (Connection c = db.getConnection()) {
            String sql = """
                INSERT INTO signed_direct_messages_history (
                    message_id, from_login, to_login, target_mode, target_session_id,
                    message_type, time_ms, nonce, raw_packet, created_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, e.getMessageId());
                ps.setString(2, e.getFromLogin());
                ps.setString(3, e.getToLogin());
                ps.setInt(4, e.getTargetMode());
                ps.setString(5, e.getTargetSessionId());
                ps.setInt(6, e.getMessageType());
                ps.setLong(7, e.getTimeMs());
                ps.setLong(8, e.getNonce());
                ps.setBytes(9, e.getRawPacket());
                ps.setLong(10, e.getCreatedAtMs());
                ps.executeUpdate();
            }
        }
    }
}
