// =======================
// shine/db/dao/BlockchainStateDAO.java   (ИЗМЕНЁННАЯ: убраны line0..7, last_block_*)
// =======================
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
                last_block_number,
                last_block_hash,
                updated_at_ms
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
        String sql = """
            INSERT INTO blockchain_state (
                blockchain_name,
                login,
                blockchain_key,
                size_limit,
                file_size_bytes,
                last_block_number,
                last_block_hash,
                updated_at_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(blockchain_name)
            DO UPDATE SET
                login            = excluded.login,
                blockchain_key   = excluded.blockchain_key,
                size_limit       = excluded.size_limit,
                file_size_bytes  = excluded.file_size_bytes,
                last_block_number= excluded.last_block_number,
                last_block_hash  = excluded.last_block_hash,
                updated_at_ms    = excluded.updated_at_ms
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;

            ps.setString(i++, e.getBlockchainName());
            ps.setString(i++, nn(e.getLogin()));
            ps.setString(i++, nn(e.getBlockchainKey()));

            ps.setLong(i++, e.getSizeLimit());
            ps.setLong(i++, e.getFileSizeBytes());

            ps.setInt(i++, e.getLastBlockNumber());
            setBytesNullable(ps, i++, e.getLastBlockHash());

            ps.setLong(i++, e.getUpdatedAtMs());

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
            return ps.executeUpdate() > 0;
        }
    }

    private BlockchainStateEntry mapRow(ResultSet rs) throws SQLException {
        BlockchainStateEntry e = new BlockchainStateEntry();

        e.setBlockchainName(rs.getString("blockchain_name"));
        e.setLogin(rs.getString("login"));
        e.setBlockchainKey(rs.getString("blockchain_key"));

        e.setSizeLimit(rs.getLong("size_limit"));
        e.setFileSizeBytes(rs.getLong("file_size_bytes"));

        e.setLastBlockNumber(rs.getInt("last_block_number"));
        e.setLastBlockHash(rs.getBytes("last_block_hash")); // nullable

        e.setUpdatedAtMs(rs.getLong("updated_at_ms"));

        return e;
    }

    private static void setBytesNullable(PreparedStatement ps, int index, byte[] b) throws SQLException {
        if (b != null) ps.setBytes(index, b);
        else ps.setNull(index, Types.BLOB);
    }

    private static String nn(String s) { return s == null ? "" : s; }
}