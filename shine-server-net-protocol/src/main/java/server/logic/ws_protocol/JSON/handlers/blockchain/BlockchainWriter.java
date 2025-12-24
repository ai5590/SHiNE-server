package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain.BchBlockEntry;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.entities.BlockEntry;
import shine.db.entities.BlockchainStateEntry;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * BlockchainDbWriter — единая точка записи блока + состояния в БД.
 *
 * Важно:
 *  - Здесь обеспечивается атомарность записи: либо вставился блок и обновилось состояние, либо не вставилось ничего.
 *  - Соединение открывается/закрывается внутри (удобно для хэндлера).
 */
public final class BlockchainWriter {

    private final SqliteDbController db;
    private final BlocksDAO blocksDAO;
    private final BlockchainStateDAO stateDAO;

    public BlockchainWriter(BlocksDAO blocksDAO, BlockchainStateDAO stateDAO) {
        this.db = SqliteDbController.getInstance();
        this.blocksDAO = blocksDAO;
        this.stateDAO = stateDAO;
    }

    public void appendBlockAndState(
            String login,
            String blockchainName,
            String prevGlobalHashHex,
            BchBlockEntry block,
            BlockchainStateEntry stOrNull,
            String newHashHex
    ) throws SQLException {

        try (Connection c = db.getConnection()) {

            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);

            try {
                // 1) блок
                insertBlockRow(c, login, blockchainName, prevGlobalHashHex, block);

                // 2) state
                appendState(c, blockchainName, block.recordNumber, stOrNull, newHashHex);

                // 3) commit
                c.commit();

            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException ignore) {}

                if (e instanceof SQLException se) throw se;
                throw new SQLException("appendBlockAndState failed", e);

            } finally {
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) {}
            }
        }
    }

    /**
     * Обновление состояния blockchain_state (создаём если отсутствует).
     * Пока линии не используются: lineIndex=0 и lineHash = globalHash.
     */
    private void appendState(
            Connection c,
            String blockchainName,
            int globalNumber,
            BlockchainStateEntry stOrNull,
            String newHashHex
    ) throws SQLException {

        BlockchainStateEntry st = stOrNull;
        if (st == null) {
            st = new BlockchainStateEntry();
            st.setBlockchainName(blockchainName);
        }

        // Последний глобальный блок
        st.setLastGlobalNumber(globalNumber);
        st.setLastGlobalHash(newHashHex);

        // Линии пока не используются: lineIndex=0 и lineHash=globalHash
        st.setLastLineNumber(0, globalNumber);
        st.setLastLineHash(0, newHashHex);

        // Метка времени обновления
        st.setUpdatedAtMs(System.currentTimeMillis());

        // UPSERT состояния
        stateDAO.upsert(c, st);
    }

    /**
     * Вставка/апдейт строки блока в blocks.
     */
    private void insertBlockRow(
            Connection c,
            String login,
            String blockchainName,
            String prevGlobalHashHex,
            BchBlockEntry block
    ) throws SQLException {

        BlockEntry e = new BlockEntry();

        // Кому принадлежит блок (логин владельца цепочки)
        e.setLogin(login);
        e.setBchName(blockchainName);

        // Глобальная нумерация
        e.setBlockGlobalNumber(block.recordNumber);
        e.setBlockGlobalPreHashe(prevGlobalHashHex);

        // Линии пока не используются: lineIndex=0, lineNumber=globalNumber
        e.setBlockLineIndex(0);
        e.setBlockLineNumber(block.recordNumber);
        e.setBlockLinePreHashe(prevGlobalHashHex);

        // msgType у тебя пока 0 (при желании позже можно ставить по Body/type)
        // ✅ Теперь сохраняем тип блока
        e.setMsgType(block.body.type());

        // Сырые байты полного блока
        e.setBlockByte(block.toBytes());

        // Поля "кому" (для сообщений/трансферов) пока пустые
        e.setToLogin(null);
        e.setToBchName(null);
        e.setToBlockGlobalNumber(null);
        e.setToBlockHashe(null);

        // UPSERT блока
        blocksDAO.upsert(c, e);
    }
}