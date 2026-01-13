// =======================
// shine/db/dao/BlocksDAO.java   (ИЗМЕНЁННЫЙ под новый blocks формат + линейная проверка)
// =======================
package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.BlockEntry;

import java.sql.*;

/**
 * DAO для таблицы blocks (новый формат).
 *
 * Правило:
 * - методы с Connection НЕ закрывают соединение
 * - методы без Connection сами открывают и закрывают соединение
 *
 * Ключ:
 * - (bch_name, block_number) — уникальная пара в рамках общей БД сервера.
 */
public final class BlocksDAO {

    private static volatile BlocksDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private BlocksDAO() { }

    public static BlocksDAO getInstance() {
        if (instance == null) {
            synchronized (BlocksDAO.class) {
                if (instance == null) instance = new BlocksDAO();
            }
        }
        return instance;
    }

    // -------------------- INSERT --------------------

    /** Вставка с внешним соединением. Соединение НЕ закрывает. */
    public void insert(Connection c, BlockEntry e) throws SQLException {
        String sql = """
            INSERT INTO blocks (
                login,
                bch_name,
                block_number,
                msg_type,
                msg_sub_type,
                block_bytes,
                to_login,
                to_bch_name,
                to_block_number,
                to_block_hash,
                block_hash,
                block_signature,
                edited_by_block_number,
                prev_line_number,
                prev_line_hash,
                this_line_number
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;

            ps.setString(i++, e.getLogin());
            ps.setString(i++, e.getBchName());
            ps.setInt(i++, e.getBlockNumber());

            ps.setInt(i++, e.getMsgType());
            ps.setInt(i++, e.getMsgSubType());

            ps.setBytes(i++, e.getBlockBytes());

            if (e.getToLogin() != null) ps.setString(i++, e.getToLogin());
            else ps.setNull(i++, Types.VARCHAR);

            if (e.getToBchName() != null) ps.setString(i++, e.getToBchName());
            else ps.setNull(i++, Types.VARCHAR);

            if (e.getToBlockNumber() != null) ps.setInt(i++, e.getToBlockNumber());
            else ps.setNull(i++, Types.INTEGER);

            if (e.getToBlockHash() != null) ps.setBytes(i++, e.getToBlockHash());
            else ps.setNull(i++, Types.BLOB);

            ps.setBytes(i++, e.getBlockHash());
            ps.setBytes(i++, e.getBlockSignature());

            if (e.getEditedByBlockNumber() != null) ps.setInt(i++, e.getEditedByBlockNumber());
            else ps.setNull(i++, Types.INTEGER);

            if (e.getPrevLineNumber() != null) ps.setInt(i++, e.getPrevLineNumber());
            else ps.setNull(i++, Types.INTEGER);

            if (e.getPrevLineHash() != null) ps.setBytes(i++, e.getPrevLineHash());
            else ps.setNull(i++, Types.BLOB);

            if (e.getThisLineNumber() != null) ps.setInt(i++, e.getThisLineNumber());
            else ps.setNull(i++, Types.INTEGER);

            ps.executeUpdate();
        }
    }

    /** Вставка без внешнего соединения. Сам открывает/закрывает. */
    public void insert(BlockEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            insert(c, e);
        }
    }

    // -------------------- SELECT: HASH BY NUMBER --------------------

    /** Получить block_hash по (bch_name, block_number). Нужен для линейной проверки. */
    public byte[] getHashByNumber(Connection c, String bchName, int blockNumber) throws SQLException {
        String sql = """
            SELECT block_hash
            FROM blocks
            WHERE bch_name = ? AND block_number = ?
            LIMIT 1
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bchName);
            ps.setInt(2, blockNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getBytes("block_hash");
            }
        }
    }

    public byte[] getHashByNumber(String bchName, int blockNumber) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getHashByNumber(c, bchName, blockNumber);
        }
    }

    // -------------------- SELECT: FULL ENTRY --------------------

    public BlockEntry getByNumber(Connection c, String bchName, int blockNumber) throws SQLException {
        String sql = """
            SELECT
                login,
                bch_name,
                block_number,
                msg_type,
                msg_sub_type,
                block_bytes,
                to_login,
                to_bch_name,
                to_block_number,
                to_block_hash,
                block_hash,
                block_signature,
                edited_by_block_number,
                prev_line_number,
                prev_line_hash,
                this_line_number
            FROM blocks
            WHERE bch_name = ? AND block_number = ?
            LIMIT 1
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bchName);
            ps.setInt(2, blockNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    public BlockEntry getByNumber(String bchName, int blockNumber) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByNumber(c, bchName, blockNumber);
        }
    }

    // -------------------- INTERNAL --------------------

    private BlockEntry mapRow(ResultSet rs) throws SQLException {
        BlockEntry e = new BlockEntry();

        e.setLogin(rs.getString("login"));
        e.setBchName(rs.getString("bch_name"));
        e.setBlockNumber(rs.getInt("block_number"));

        e.setMsgType(rs.getInt("msg_type"));
        e.setMsgSubType(rs.getInt("msg_sub_type"));

        e.setBlockBytes(rs.getBytes("block_bytes"));

        String toLogin = rs.getString("to_login");
        if (rs.wasNull()) toLogin = null;
        e.setToLogin(toLogin);

        String toBchName = rs.getString("to_bch_name");
        if (rs.wasNull()) toBchName = null;
        e.setToBchName(toBchName);

        Integer toBlockNumber = (Integer) rs.getObject("to_block_number");
        e.setToBlockNumber(toBlockNumber);

        byte[] toHash = rs.getBytes("to_block_hash");
        if (rs.wasNull()) toHash = null;
        e.setToBlockHash(toHash);

        e.setBlockHash(rs.getBytes("block_hash"));
        e.setBlockSignature(rs.getBytes("block_signature"));

        Integer editedBy = (Integer) rs.getObject("edited_by_block_number");
        e.setEditedByBlockNumber(editedBy);

        Integer prevLn = (Integer) rs.getObject("prev_line_number");
        e.setPrevLineNumber(prevLn);

        byte[] prevLh = rs.getBytes("prev_line_hash");
        if (rs.wasNull()) prevLh = null;
        e.setPrevLineHash(prevLh);

        Integer thisLn = (Integer) rs.getObject("this_line_number");
        e.setThisLineNumber(thisLn);

        return e;
    }
}