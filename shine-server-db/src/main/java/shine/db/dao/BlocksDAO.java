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
                bchName,
                blockGlobalNumber,
                blockGlobalPreHashe,
                blockLineIndex,
                blockLineNumber,
                blockLinePreHashe,
                msgType,
                blockByte,
                to_login,
                toBchName,
                toBlockGlobalNumber,
                toBlockHashe
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    /**
     * Сохранить (условный upsert) с внешним соединением. Соединение НЕ закрывает.
     * Без PK/UNIQUE делаем: UPDATE по "ключевым" полям -> если 0 строк, то INSERT.
     */
    public void upsert(Connection c, BlockEntry e) throws SQLException {
        int updated = update(c, e);
        if (updated == 0) insert(c, e);
    }

    /** Сохранить (upsert) без внешнего соединения. Сам открывает/закрывает. */
    public void upsert(BlockEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            upsert(c, e);
        }
    }

    // -------------------- SELECT --------------------

    /** Получить блок по "PK-подобному" набору полей с внешним соединением. Соединение НЕ закрывает. */
    public BlockEntry getByPk(Connection c,
                             String login,
                             String bchName,
                             int blockGlobalNumber,
                             int blockLineIndex,
                             int blockLineNumber) throws SQLException {

        String sql = """
            SELECT
                login,
                bchName,
                blockGlobalNumber,
                blockGlobalPreHashe,
                blockLineIndex,
                blockLineNumber,
                blockLinePreHashe,
                msgType,
                blockByte,
                to_login,
                toBchName,
                toBlockGlobalNumber,
                toBlockHashe
            FROM blocks
            WHERE
                login = ?
                AND bchName = ?
                AND blockGlobalNumber = ?
                AND blockLineIndex = ?
                AND blockLineNumber = ?
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

    /** Получить блок по "PK-подобному" набору полей без внешнего соединения. Сам открывает/закрывает. */
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

    /**
     * Обновить (строго UPDATE) по "PK-подобному" набору полей с внешним соединением. Соединение НЕ закрывает.
     * Может обновить >1 строк, если в таблице появились дубликаты.
     */
    public int update(Connection c, BlockEntry e) throws SQLException {
        String sql = """
            UPDATE blocks
            SET
                blockGlobalPreHashe   = ?,
                blockLinePreHashe     = ?,
                msgType               = ?,
                blockByte             = ?,
                to_login              = ?,
                toBchName             = ?,
                toBlockGlobalNumber   = ?,
                toBlockHashe          = ?
            WHERE
                login = ?
                AND bchName = ?
                AND blockGlobalNumber = ?
                AND blockLineIndex = ?
                AND blockLineNumber = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;

            ps.setString(i++, nn(e.getBlockGlobalPreHashe()));
            ps.setString(i++, nn(e.getBlockLinePreHashe()));
            ps.setInt(i++, e.getMsgType());

            byte[] bytes = e.getBlockByte();
            if (bytes != null) ps.setBytes(i++, bytes);
            else ps.setNull(i++, Types.BLOB);

            if (e.getToLogin() != null) ps.setString(i++, e.getToLogin());
            else ps.setNull(i++, Types.VARCHAR);

            ps.setString(i++, nn(e.getToBchName()));
            ps.setInt(i++, e.getToBlockGlobalNumber());
            ps.setString(i++, nn(e.getToBlockHashe()));

            ps.setString(i++, e.getLogin());
            ps.setString(i++, e.getBchName());
            ps.setInt(i++, e.getBlockGlobalNumber());
            ps.setInt(i++, e.getBlockLineIndex());
            ps.setInt(i++, e.getBlockLineNumber());

            return ps.executeUpdate();
        }
    }

    /** Обновить без внешнего соединения. Сам открывает/закрывает. */
    public int update(BlockEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            return update(c, e);
        }
    }

    // -------------------- DELETE --------------------

    /**
     * Удалить по "PK-подобному" набору полей с внешним соединением. Соединение НЕ закрывает.
     * Может удалить >1 строк, если есть дубликаты.
     */
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
                AND bchName = ?
                AND blockGlobalNumber = ?
                AND blockLineIndex = ?
                AND blockLineNumber = ?
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

    /** Удалить по "PK-подобному" набору полей без внешнего соединения. Сам открывает/закрывает. */
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

    /** Единая привязка параметров под INSERT — чтобы не разъезжалось. */
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

        byte[] bytes = e.getBlockByte();
        if (bytes != null) ps.setBytes(i++, bytes);
        else ps.setNull(i++, Types.BLOB);

        if (e.getToLogin() != null) ps.setString(i++, e.getToLogin());
        else ps.setNull(i++, Types.VARCHAR);

        ps.setString(i++, nn(e.getToBchName()));
        ps.setInt(i++, e.getToBlockGlobalNumber());
        ps.setString(i++, nn(e.getToBlockHashe()));
    }

    private BlockEntry mapRow(ResultSet rs) throws SQLException {
        BlockEntry e = new BlockEntry();

        e.setLogin(rs.getString("login"));
        e.setBchName(rs.getString("bchName"));
        e.setBlockGlobalNumber(rs.getInt("blockGlobalNumber"));
        e.setBlockGlobalPreHashe(rs.getString("blockGlobalPreHashe"));

        e.setBlockLineIndex(rs.getInt("blockLineIndex"));
        e.setBlockLineNumber(rs.getInt("blockLineNumber"));
        e.setBlockLinePreHashe(rs.getString("blockLinePreHashe"));

        e.setMsgType(rs.getInt("msgType"));

        e.setBlockByte(rs.getBytes("blockByte"));

        e.setToLogin(rs.getString("to_login"));
        e.setToBchName(rs.getString("toBchName"));
        e.setToBlockGlobalNumber(rs.getInt("toBlockGlobalNumber"));
        e.setToBlockHashe(rs.getString("toBlockHashe"));

        return e;
    }

    private static String nn(String s) { return s == null ? "" : s; }
}