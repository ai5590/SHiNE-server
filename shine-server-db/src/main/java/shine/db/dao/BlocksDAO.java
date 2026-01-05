package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.BlockEntry;

import java.sql.*;

/**
 * DAO для таблицы blocks.
 *
 * Правило:
 * - методы с Connection НЕ закрывают соединение
 * - методы без Connection сами открывают и закрывают соединение
 *
 * Важно:
 * - PRIMARY KEY удалён (временно), поэтому "upsert" сделан через UPDATE->INSERT.
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
                block_global_number,
                block_global_pre_hashe,
                block_line_index,
                block_line_number,
                block_line_pre_hashe,
                msg_type,
                msg_sub_type,
                block_byte,
                to_login,
                to_bch_name,
                to_block_global_number,
                to_block_hashe
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bindAll(ps, e);
            ps.executeUpdate();
        }
    }

    /** Вставка без внешнего соединения. Сам открывает/закрывает. */
    public void insert(BlockEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            insert(c, e);
        }
    }

    // -------------------- UPSERT (UPDATE -> INSERT) --------------------

    public void upsert(Connection c, BlockEntry e) throws SQLException {
        int updated = update(c, e);
        if (updated == 0) insert(c, e);
    }

    public void upsert(BlockEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            upsert(c, e);
        }
    }

    // -------------------- SELECT --------------------

    public BlockEntry getByPk(Connection c,
                             String login,
                             String bchName,
                             int blockGlobalNumber,
                             int blockLineIndex,
                             int blockLineNumber) throws SQLException {

        String sql = """
            SELECT
                login,
                bch_name,
                block_global_number,
                block_global_pre_hashe,
                block_line_index,
                block_line_number,
                block_line_pre_hashe,
                msg_type,
                msg_sub_type,
                block_byte,
                to_login,
                to_bch_name,
                to_block_global_number,
                to_block_hashe
            FROM blocks
            WHERE
                login = ?
                AND bch_name = ?
                AND block_global_number = ?
                AND block_line_index = ?
                AND block_line_number = ?
            LIMIT 1
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, bchName);
            ps.setInt(3, blockGlobalNumber);
            ps.setInt(4, blockLineIndex);
            ps.setInt(5, blockLineNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    public BlockEntry getByPk(String login,
                             String bchName,
                             int blockGlobalNumber,
                             int blockLineIndex,
                             int blockLineNumber) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByPk(c, login, bchName, blockGlobalNumber, blockLineIndex, blockLineNumber);
        }
    }

    // -------------------- UPDATE --------------------

    public int update(Connection c, BlockEntry e) throws SQLException {
        String sql = """
            UPDATE blocks
            SET
                block_global_pre_hashe  = ?,
                block_line_pre_hashe    = ?,
                msg_type                = ?,
                msg_sub_type            = ?,
                block_byte              = ?,
                to_login                = ?,
                to_bch_name             = ?,
                to_block_global_number  = ?,
                to_block_hashe          = ?
            WHERE
                login = ?
                AND bch_name = ?
                AND block_global_number = ?
                AND block_line_index = ?
                AND block_line_number = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;

            ps.setString(i++, nn(e.getBlockGlobalPreHashe()));
            ps.setString(i++, nn(e.getBlockLinePreHashe()));
            ps.setInt(i++, e.getMsgType());
            ps.setInt(i++, e.getMsgSubType());

            byte[] bytes = e.getBlockByte();
            if (bytes != null) ps.setBytes(i++, bytes);
            else ps.setNull(i++, Types.BLOB);

            if (e.getToLogin() != null) ps.setString(i++, e.getToLogin());
            else ps.setNull(i++, Types.VARCHAR);

            if (e.getToBchName() != null) ps.setString(i++, e.getToBchName());
            else ps.setNull(i++, Types.VARCHAR);

            if (e.getToBlockGlobalNumber() != null) ps.setInt(i++, e.getToBlockGlobalNumber());
            else ps.setNull(i++, Types.INTEGER);

            if (e.getToBlockHashe() != null) ps.setString(i++, e.getToBlockHashe());
            else ps.setNull(i++, Types.VARCHAR);

            ps.setString(i++, e.getLogin());
            ps.setString(i++, e.getBchName());
            ps.setInt(i++, e.getBlockGlobalNumber());
            ps.setInt(i++, e.getBlockLineIndex());
            ps.setInt(i++, e.getBlockLineNumber());

            return ps.executeUpdate();
        }
    }

    public int update(BlockEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            return update(c, e);
        }
    }

    // -------------------- DELETE --------------------

    public int deleteByPk(Connection c,
                          String login,
                          String bchName,
                          int blockGlobalNumber,
                          int blockLineIndex,
                          int blockLineNumber) throws SQLException {

        String sql = """
            DELETE FROM blocks
            WHERE
                login = ?
                AND bch_name = ?
                AND block_global_number = ?
                AND block_line_index = ?
                AND block_line_number = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, bchName);
            ps.setInt(3, blockGlobalNumber);
            ps.setInt(4, blockLineIndex);
            ps.setInt(5, blockLineNumber);
            return ps.executeUpdate();
        }
    }

    public int deleteByPk(String login,
                          String bchName,
                          int blockGlobalNumber,
                          int blockLineIndex,
                          int blockLineNumber) throws SQLException {
        try (Connection c = db.getConnection()) {
            return deleteByPk(c, login, bchName, blockGlobalNumber, blockLineIndex, blockLineNumber);
        }
    }

    // -------------------- INTERNAL --------------------

    private static void bindAll(PreparedStatement ps, BlockEntry e) throws SQLException {
        int i = 1;

        ps.setString(i++, e.getLogin());
        ps.setString(i++, e.getBchName());
        ps.setInt(i++, e.getBlockGlobalNumber());
        ps.setString(i++, nn(e.getBlockGlobalPreHashe()));

        ps.setInt(i++, e.getBlockLineIndex());
        ps.setInt(i++, e.getBlockLineNumber());
        ps.setString(i++, nn(e.getBlockLinePreHashe()));

        ps.setInt(i++, e.getMsgType());
        ps.setInt(i++, e.getMsgSubType());

        byte[] bytes = e.getBlockByte();
        if (bytes != null) ps.setBytes(i++, bytes);
        else ps.setNull(i++, Types.BLOB);

        if (e.getToLogin() != null) ps.setString(i++, e.getToLogin());
        else ps.setNull(i++, Types.VARCHAR);

        if (e.getToBchName() != null) ps.setString(i++, e.getToBchName());
        else ps.setNull(i++, Types.VARCHAR);

        if (e.getToBlockGlobalNumber() != null) ps.setInt(i++, e.getToBlockGlobalNumber());
        else ps.setNull(i++, Types.INTEGER);

        if (e.getToBlockHashe() != null) ps.setString(i++, e.getToBlockHashe());
        else ps.setNull(i++, Types.VARCHAR);
    }

    private BlockEntry mapRow(ResultSet rs) throws SQLException {
        BlockEntry e = new BlockEntry();

        e.setLogin(rs.getString("login"));
        e.setBchName(rs.getString("bch_name"));
        e.setBlockGlobalNumber(rs.getInt("block_global_number"));
        e.setBlockGlobalPreHashe(rs.getString("block_global_pre_hashe"));

        e.setBlockLineIndex(rs.getInt("block_line_index"));
        e.setBlockLineNumber(rs.getInt("block_line_number"));
        e.setBlockLinePreHashe(rs.getString("block_line_pre_hashe"));

        e.setMsgType(rs.getInt("msg_type"));
        e.setMsgSubType(rs.getInt("msg_sub_type"));

        e.setBlockByte(rs.getBytes("block_byte"));

        e.setToLogin(rs.getString("to_login"));

        String toBchName = rs.getString("to_bch_name");
        if (rs.wasNull()) toBchName = null;
        e.setToBchName(toBchName);

        Integer toBlockGlobalNumber = (Integer) rs.getObject("to_block_global_number");
        e.setToBlockGlobalNumber(toBlockGlobalNumber);

        String toBlockHashe = rs.getString("to_block_hashe");
        if (rs.wasNull()) toBlockHashe = null;
        e.setToBlockHashe(toBlockHashe);

        return e;
    }

    private static String nn(String s) { return s == null ? "" : s; }
}