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
 * - PRIMARY KEY: (loginId, blockchainId, blockGlobalNumber, blockLineIndex, blockLineNumber)
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
                loginId,
                blockchainId,
                blockGlobalNumber,
                blockGlobalPreHashe,
                blockLineIndex,
                blockLineNumber,
                blockLinePreHashe,
                msgType,
                blockByte,
                toLoginId,
                toBlockchainId,
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

    // -------------------- UPSERT --------------------

    /**
     * Сохранить (upsert) с внешним соединением. Соединение НЕ закрывает.
     * Если запись с таким PK уже есть — обновляем поля.
     */
    public void upsert(Connection c, BlockEntry e) throws SQLException {
        String sql = """
            INSERT INTO blocks (
                loginId,
                blockchainId,
                blockGlobalNumber,
                blockGlobalPreHashe,
                blockLineIndex,
                blockLineNumber,
                blockLinePreHashe,
                msgType,
                blockByte,
                toLoginId,
                toBlockchainId,
                toBlockGlobalNumber,
                toBlockHashe
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(loginId, blockchainId, blockGlobalNumber, blockLineIndex, blockLineNumber)
            DO UPDATE SET
                blockGlobalPreHashe   = excluded.blockGlobalPreHashe,
                blockLinePreHashe     = excluded.blockLinePreHashe,
                msgType               = excluded.msgType,
                blockByte             = excluded.blockByte,
                toLoginId             = excluded.toLoginId,
                toBlockchainId        = excluded.toBlockchainId,
                toBlockGlobalNumber   = excluded.toBlockGlobalNumber,
                toBlockHashe          = excluded.toBlockHashe
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bindAll(ps, e);
            ps.executeUpdate();
        }
    }

    /** Сохранить (upsert) без внешнего соединения. Сам открывает/закрывает. */
    public void upsert(BlockEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            upsert(c, e);
        }
    }

    // -------------------- SELECT --------------------

    /** Получить блок по PK с внешним соединением. Соединение НЕ закрывает. */
    public BlockEntry getByPk(Connection c,
                             long loginId,
                             long blockchainId,
                             int blockGlobalNumber,
                             int blockLineIndex,
                             int blockLineNumber) throws SQLException {

        String sql = """
            SELECT
                loginId,
                blockchainId,
                blockGlobalNumber,
                blockGlobalPreHashe,
                blockLineIndex,
                blockLineNumber,
                blockLinePreHashe,
                msgType,
                blockByte,
                toLoginId,
                toBlockchainId,
                toBlockGlobalNumber,
                toBlockHashe
            FROM blocks
            WHERE
                loginId = ?
                AND blockchainId = ?
                AND blockGlobalNumber = ?
                AND blockLineIndex = ?
                AND blockLineNumber = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, loginId);
            ps.setLong(2, blockchainId);
            ps.setInt(3, blockGlobalNumber);
            ps.setInt(4, blockLineIndex);
            ps.setInt(5, blockLineNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /** Получить блок по PK без внешнего соединения. Сам открывает/закрывает. */
    public BlockEntry getByPk(long loginId,
                             long blockchainId,
                             int blockGlobalNumber,
                             int blockLineIndex,
                             int blockLineNumber) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByPk(c, loginId, blockchainId, blockGlobalNumber, blockLineIndex, blockLineNumber);
        }
    }

    // -------------------- UPDATE --------------------

    /**
     * Обновить (строго UPDATE) по PK с внешним соединением. Соединение НЕ закрывает.
     * Если строки нет — updateCount будет 0.
     */
    public int update(Connection c, BlockEntry e) throws SQLException {
        String sql = """
            UPDATE blocks
            SET
                blockGlobalPreHashe   = ?,
                blockLinePreHashe     = ?,
                msgType               = ?,
                blockByte             = ?,
                toLoginId             = ?,
                toBlockchainId        = ?,
                toBlockGlobalNumber   = ?,
                toBlockHashe          = ?
            WHERE
                loginId = ?
                AND blockchainId = ?
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

            ps.setLong(i++, e.getToLoginId());
            ps.setInt(i++, e.getToBlockchainId());
            ps.setInt(i++, e.getToBlockGlobalNumber());
            ps.setString(i++, nn(e.getToBlockHashe()));

            ps.setLong(i++, e.getLoginId());
            ps.setLong(i++, e.getBlockchainId());
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

    /** Удалить по PK с внешним соединением. Соединение НЕ закрывает. */
    public int deleteByPk(Connection c,
                          long loginId,
                          long blockchainId,
                          int blockGlobalNumber,
                          int blockLineIndex,
                          int blockLineNumber) throws SQLException {

        String sql = """
            DELETE FROM blocks
            WHERE
                loginId = ?
                AND blockchainId = ?
                AND blockGlobalNumber = ?
                AND blockLineIndex = ?
                AND blockLineNumber = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, loginId);
            ps.setLong(2, blockchainId);
            ps.setInt(3, blockGlobalNumber);
            ps.setInt(4, blockLineIndex);
            ps.setInt(5, blockLineNumber);
            return ps.executeUpdate();
        }
    }

    /** Удалить по PK без внешнего соединения. Сам открывает/закрывает. */
    public int deleteByPk(long loginId,
                          long blockchainId,
                          int blockGlobalNumber,
                          int blockLineIndex,
                          int blockLineNumber) throws SQLException {
        try (Connection c = db.getConnection()) {
            return deleteByPk(c, loginId, blockchainId, blockGlobalNumber, blockLineIndex, blockLineNumber);
        }
    }

    // -------------------- INTERNAL --------------------

    /** Единая привязка параметров под INSERT/UPSERT — чтобы не разъезжалось. */
    private static void bindAll(PreparedStatement ps, BlockEntry e) throws SQLException {
        int i = 1;

        ps.setLong(i++, e.getLoginId());
        ps.setLong(i++, e.getBlockchainId());
        ps.setInt(i++, e.getBlockGlobalNumber());
        ps.setString(i++, nn(e.getBlockGlobalPreHashe()));

        ps.setInt(i++, e.getBlockLineIndex());
        ps.setInt(i++, e.getBlockLineNumber());
        ps.setString(i++, nn(e.getBlockLinePreHashe()));

        ps.setInt(i++, e.getMsgType());

        byte[] bytes = e.getBlockByte();
        if (bytes != null) ps.setBytes(i++, bytes);
        else ps.setNull(i++, Types.BLOB);

        ps.setLong(i++, e.getToLoginId());
        ps.setInt(i++, e.getToBlockchainId());
        ps.setInt(i++, e.getToBlockGlobalNumber());
        ps.setString(i++, nn(e.getToBlockHashe()));
    }

    private BlockEntry mapRow(ResultSet rs) throws SQLException {
        BlockEntry e = new BlockEntry();

        e.setLoginId(rs.getLong("loginId"));
        e.setBlockchainId(rs.getLong("blockchainId"));
        e.setBlockGlobalNumber(rs.getInt("blockGlobalNumber"));
        e.setBlockGlobalPreHashe(rs.getString("blockGlobalPreHashe"));

        e.setBlockLineIndex(rs.getInt("blockLineIndex"));
        e.setBlockLineNumber(rs.getInt("blockLineNumber"));
        e.setBlockLinePreHashe(rs.getString("blockLinePreHashe"));

        e.setMsgType(rs.getInt("msgType"));

        e.setBlockByte(rs.getBytes("blockByte"));

        e.setToLoginId(rs.getLong("toLoginId"));
        e.setToBlockchainId(rs.getInt("toBlockchainId"));
        e.setToBlockGlobalNumber(rs.getInt("toBlockGlobalNumber"));
        e.setToBlockHashe(rs.getString("toBlockHashe"));

        return e;
    }

    private static String nn(String s) { return s == null ? "" : s; }
}