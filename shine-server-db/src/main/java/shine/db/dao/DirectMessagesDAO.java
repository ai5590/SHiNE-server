package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.DirectMessageEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;

public final class DirectMessagesDAO {
    private static volatile DirectMessagesDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private DirectMessagesDAO() {}

    public static DirectMessagesDAO getInstance() {
        if (instance == null) {
            synchronized (DirectMessagesDAO.class) {
                if (instance == null) instance = new DirectMessagesDAO();
            }
        }
        return instance;
    }

    public void insert(DirectMessageEntry entry) throws Exception {
        try (Connection c = db.getConnection()) {
            String sql = """
                INSERT INTO direct_messages (
                    message_id, from_login, to_login, text, created_at_ms
                ) VALUES (?, ?, ?, ?, ?)
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, entry.getMessageId());
                ps.setString(2, entry.getFromLogin());
                ps.setString(3, entry.getToLogin());
                ps.setString(4, entry.getText());
                ps.setLong(5, entry.getCreatedAtMs());
                ps.executeUpdate();
            }
        }
    }

    public boolean existsFromTo(String fromLogin, String toLogin) throws Exception {
        try (Connection c = db.getConnection()) {
            String sql = "SELECT 1 FROM direct_messages WHERE from_login = ? AND to_login = ? LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, fromLogin);
                ps.setString(2, toLogin);
                return ps.executeQuery().next();
            }
        }
    }

}
