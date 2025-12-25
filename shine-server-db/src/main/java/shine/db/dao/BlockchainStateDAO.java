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

    /** Получить по blockchainName без внешнего соединения. Сам открывает/закрывает. */
    public BlockchainStateEntry getByBlockchainName(String blockchainName) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByBlockchainName(c, blockchainName);
        }
    }

    /** Получить по blockchainName с внешним соединением. Соединение НЕ закрывает. */
    public BlockchainStateEntry getByBlockchainName(Connection c, String blockchainName) throws SQLException {
        String sql = """
            SELECT
                blockchainName,
                login,
                public_key_base64,
                size_limit,
                size_bytes,
                file_size_bytes,
                last_global_number,
                last_global_hash,
                updated_at_ms,
                line0_last_number, line0_last_hash,
                line1_last_number, line1_last_hash,
                line2_last_number, line2_last_hash,
                line3_last_number, line3_last_hash,
                line4_last_number, line4_last_hash,
                line5_last_number, line5_last_hash,
                line6_last_number, line6_last_hash,
                line7_last_number, line7_last_hash
            FROM blockchain_state
            WHERE blockchainName = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, blockchainName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /** UPSERT без внешнего соединения. Сам открывает/закрывает. */
    public void upsert(BlockchainStateEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            upsert(c, e);
        }
    }

    /** UPSERT с внешним соединением. Соединение НЕ закрывает. */
    public void upsert(Connection c, BlockchainStateEntry e) throws SQLException {
        String sql = """
            INSERT INTO blockchain_state (
                blockchainName,
                login,
                public_key_base64,
                size_limit,
                size_bytes,
                file_size_bytes,
                last_global_number,
                last_global_hash,
                updated_at_ms,
                line0_last_number, line0_last_hash,
                line1_last_number, line1_last_hash,
                line2_last_number, line2_last_hash,
                line3_last_number, line3_last_hash,
                line4_last_number, line4_last_hash,
                line5_last_number, line5_last_hash,
                line6_last_number, line6_last_hash,
                line7_last_number, line7_last_hash
            ) VALUES (
                ?,?,?,?,?,?,?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?,
                ?,?
            )
            ON CONFLICT(blockchainName)
            DO UPDATE SET
                login              = excluded.login,
                public_key_base64  = excluded.public_key_base64,
                size_limit         = excluded.size_limit,
                size_bytes         = excluded.size_bytes,
                file_size_bytes    = excluded.file_size_bytes,
                last_global_number = excluded.last_global_number,
                last_global_hash   = excluded.last_global_hash,
                updated_at_ms      = excluded.updated_at_ms,
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
                line7_last_hash    = excluded.line7_last_hash
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;

            ps.setString(i++, e.getBlockchainName());
            ps.setString(i++, nn(e.getLogin()));
            ps.setString(i++, nn(e.getPublicKeyBase64()));
            ps.setLong(i++, e.getSizeLimit());
            ps.setLong(i++, e.getFileSizeBytes());
            ps.setInt(i++, e.getLastGlobalNumber());
            ps.setString(i++, nn(e.getLastGlobalHash()));
            ps.setLong(i++, e.getUpdatedAtMs());

            for (int line = 0; line < 8; line++) {
                ps.setInt(i++, e.getLastLineNumber(line));
                ps.setString(i++, nn(e.getLastLineHash(line)));
            }

            ps.executeUpdate();
        }
    }

    private BlockchainStateEntry mapRow(ResultSet rs) throws SQLException {
        BlockchainStateEntry e = new BlockchainStateEntry();

        e.setBlockchainName(rs.getString("blockchainName"));
        e.setLogin(rs.getString("login"));
        e.setPublicKeyBase64(rs.getString("public_key_base64"));

        e.setSizeLimit(rs.getInt("size_limit"));
        e.setFileSizeBytes(rs.getLong("file_size_bytes"));

        e.setLastGlobalNumber(rs.getInt("last_global_number"));
        e.setLastGlobalHash(rs.getString("last_global_hash"));

        e.setUpdatedAtMs(rs.getLong("updated_at_ms"));

        for (int line = 0; line < 8; line++) {
            e.setLastLineNumber(line, rs.getInt("line" + line + "_last_number"));
            e.setLastLineHash(line, rs.getString("line" + line + "_last_hash"));
        }

        return e;
    }

    private static String nn(String s) { return s == null ? "" : s; }
}