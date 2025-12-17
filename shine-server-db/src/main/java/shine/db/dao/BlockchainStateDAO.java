package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.BlockchainStateEntry;

import java.sql.*;

public final class BlockchainStateDAO {

    private static volatile BlockchainStateDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private BlockchainStateDAO() {}

    public static BlockchainStateDAO getInstance() {
        if (instance == null) {
            synchronized (BlockchainStateDAO.class) {
                if (instance == null) instance = new BlockchainStateDAO();
            }
        }
        return instance;
    }

    // --- Новый вариант: работа на переданном соединении ---
    public BlockchainStateEntry getByBlockchainId(Connection conn, long blockchainId) throws SQLException {
        String sql = """
            SELECT
                blockchain_id,
                user_login,
                public_key_base64,
                size_limit,
                size_bytes,
                last_global_number,
                last_global_hash,
                line0_last_number, line0_last_hash,
                line1_last_number, line1_last_hash,
                line2_last_number, line2_last_hash,
                line3_last_number, line3_last_hash,
                line4_last_number, line4_last_hash,
                line5_last_number, line5_last_hash,
                line6_last_number, line6_last_hash,
                line7_last_number, line7_last_hash,
                updated_at_ms
            FROM blockchain_state
            WHERE blockchain_id = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, blockchainId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    // Старый вариант: сам открывает/закрывает conn
    public BlockchainStateEntry getByBlockchainId(long blockchainId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            return getByBlockchainId(conn, blockchainId);
        }
    }

    // --- Новый вариант: UPSERT на переданном соединении ---
    public void upsert(Connection conn, BlockchainStateEntry e) throws SQLException {
        long now = System.currentTimeMillis();
        if (e.getUpdatedAtMs() <= 0) e.setUpdatedAtMs(now);

        String sql = """
            INSERT INTO blockchain_state (
                blockchain_id,
                user_login,
                public_key_base64,
                size_limit,
                size_bytes,
                last_global_number,
                last_global_hash,
                line0_last_number, line0_last_hash,
                line1_last_number, line1_last_hash,
                line2_last_number, line2_last_hash,
                line3_last_number, line3_last_hash,
                line4_last_number, line4_last_hash,
                line5_last_number, line5_last_hash,
                line6_last_number, line6_last_hash,
                line7_last_number, line7_last_hash,
                updated_at_ms
            ) VALUES (
                ?,?,?,?,?,?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?
            )
            ON CONFLICT(blockchain_id)
            DO UPDATE SET
                user_login         = excluded.user_login,
                public_key_base64  = excluded.public_key_base64,
                size_limit         = excluded.size_limit,
                size_bytes         = excluded.size_bytes,
                last_global_number = excluded.last_global_number,
                last_global_hash   = excluded.last_global_hash,

                line0_last_number  = excluded.line0_last_number,
                line0_last_hash    = excluded.line0_last_hash,
                line1_last_number  = excluded.line1_last_number,
                line1_last_hash    = excluded.line1_last_hash,
                line2_last_number  = excluded.line2_last_number,
                line2_last_hash    = excluded.line2_last_hash,
                line3_last_number  = excluded.line3_last_number,
                line3_last_hash    = excluded.line3_last_hash,
                line4_last_number  = excluded.line4_last_number,
                line4_last_hash    = excluded.line4_last_hash,
                line5_last_number  = excluded.line5_last_number,
                line5_last_hash    = excluded.line5_last_hash,
                line6_last_number  = excluded.line6_last_number,
                line6_last_hash    = excluded.line6_last_hash,
                line7_last_number  = excluded.line7_last_number,
                line7_last_hash    = excluded.line7_last_hash,

                updated_at_ms      = excluded.updated_at_ms
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, e.getBlockchainId());
            ps.setString(i++, nn(e.getUserLogin()));
            ps.setString(i++, nn(e.getPublicKeyBase64()));
            ps.setInt(i++, e.getSizeLimit());
            ps.setInt(i++, e.getSizeBytes());
            ps.setInt(i++, e.getLastGlobalNumber());
            ps.setString(i++, nn(e.getLastGlobalHash()));

            for (int line = 0; line < 8; line++) {
                ps.setInt(i++, e.getLastLineNumber(line));
                ps.setString(i++, nn(e.getLastLineHash(line)));
            }

            ps.setLong(i++, e.getUpdatedAtMs());
            ps.executeUpdate();
        }
    }

    // Старый вариант: сам открывает/закрывает conn
    public void upsert(BlockchainStateEntry e) throws SQLException {
        try (Connection conn = db.getConnection()) {
            upsert(conn, e);
        }
    }

    private BlockchainStateEntry mapRow(ResultSet rs) throws SQLException {
        BlockchainStateEntry e = new BlockchainStateEntry();
        e.setBlockchainId(rs.getLong("blockchain_id"));
        e.setUserLogin(rs.getString("user_login"));
        e.setPublicKeyBase64(rs.getString("public_key_base64"));

        e.setSizeLimit(rs.getInt("size_limit"));
        e.setSizeBytes(rs.getInt("size_bytes"));

        e.setLastGlobalNumber(rs.getInt("last_global_number"));
        e.setLastGlobalHash(rs.getString("last_global_hash"));

        for (int line = 0; line < 8; line++) {
            e.setLastLineNumber(line, rs.getInt("line" + line + "_last_number"));
            e.setLastLineHash(line, rs.getString("line" + line + "_last_hash"));
        }

        e.setUpdatedAtMs(rs.getLong("updated_at_ms"));
        return e;
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }
}