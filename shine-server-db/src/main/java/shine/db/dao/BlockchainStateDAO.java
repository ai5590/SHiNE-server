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
                blockchain_name,
                login,
                blockchain_key,
                size_limit,
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
            WHERE blockchain_name = ?
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

        // ВАЖНО:
        // Колонок должно быть ровно 24:
        //  8 основных + (8 линий * 2 поля) = 8 + 16 = 24
        //
        // size_bytes УДАЛЁН ИЗ ПРОЕКТА, здесь его быть не должно.

        String sql = """
            INSERT INTO blockchain_state (
                blockchain_name,
                login,
                blockchain_key,
                size_limit,
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
            ON CONFLICT(blockchain_name)
            DO UPDATE SET
                login              = excluded.login,
                blockchain_key     = excluded.blockchain_key,
                size_limit         = excluded.size_limit,
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
            ps.setString(i++, nn(e.getBlockchainKey()));

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

    /**
     * Атомарно увеличить file_size_bytes на deltaBytes, но только если НЕ превысим size_limit.
     */
    public boolean tryIncreaseFileSizeWithinLimit(Connection c, String blockchainName, long deltaBytes, long nowMs) throws SQLException {
        String sql = """
            UPDATE blockchain_state
            SET
                file_size_bytes = file_size_bytes + ?,
                updated_at_ms   = ?
            WHERE
                blockchain_name = ?
                AND (file_size_bytes + ?) <= size_limit
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, deltaBytes);
            ps.setLong(2, nowMs);
            ps.setString(3, blockchainName);
            ps.setLong(4, deltaBytes);
            int updated = ps.executeUpdate();
            return updated > 0;
        }
    }

    /** Удобная проверка для HEADER: запись должна быть и last_global_number должен быть -1. */
    public BlockchainStateEntry requireExistingAtGenesis(Connection c, String blockchainName) throws SQLException {
        BlockchainStateEntry st = getByBlockchainName(c, blockchainName);
        if (st == null) {
            throw new IllegalStateException("Blockchain state not found for blockchainName=" + blockchainName);
        }
        if (st.getLastGlobalNumber() != -1) {
            throw new IllegalStateException("Blockchain state is not at genesis (-1). blockchainName=" + blockchainName +
                    " last_global_number=" + st.getLastGlobalNumber());
        }
        return st;
    }

    private BlockchainStateEntry mapRow(ResultSet rs) throws SQLException {
        BlockchainStateEntry e = new BlockchainStateEntry();

        e.setBlockchainName(rs.getString("blockchain_name"));
        e.setLogin(rs.getString("login"));
        e.setBlockchainKey(rs.getString("blockchain_key"));

        e.setSizeLimit(rs.getLong("size_limit"));
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