package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.ChannelNameStateEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class ChannelNameStateDAO {
    private static volatile ChannelNameStateDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private ChannelNameStateDAO() {}

    public static ChannelNameStateDAO getInstance() {
        if (instance == null) {
            synchronized (ChannelNameStateDAO.class) {
                if (instance == null) instance = new ChannelNameStateDAO();
            }
        }
        return instance;
    }

    public boolean existsBySlug(Connection c, String slug) throws SQLException {
        String sql = "SELECT 1 FROM channel_names_state WHERE slug = ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsBySlug(String slug) throws SQLException {
        try (Connection c = db.getConnection()) {
            return existsBySlug(c, slug);
        }
    }

    public void clearAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM channel_names_state")) {
            ps.executeUpdate();
        }
    }

    public void insert(Connection c, ChannelNameStateEntry entry) throws SQLException {
        String sql = """
                INSERT INTO channel_names_state (
                    slug,
                    display_name,
                    owner_login,
                    owner_bch_name,
                    channel_root_block_number,
                    channel_root_block_hash,
                    created_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entry.getSlug());
            ps.setString(2, entry.getDisplayName());
            ps.setString(3, entry.getOwnerLogin());
            ps.setString(4, entry.getOwnerBlockchainName());
            ps.setInt(5, entry.getChannelRootBlockNumber());
            ps.setBytes(6, entry.getChannelRootBlockHash());
            ps.setLong(7, entry.getCreatedAtMs());
            ps.executeUpdate();
        }
    }

    public void insertAll(Connection c, List<ChannelNameStateEntry> entries) throws SQLException {
        for (ChannelNameStateEntry entry : entries) {
            insert(c, entry);
        }
    }
}
