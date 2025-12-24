package server.logic.ws_protocol.JSON.handlers.blockchain;

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
 *  - При необходимости можно вызвать appendBlockAndState(Connection, ...) и управлять транзакцией снаружи.
 */
public final class BlockchainDbWriter {

    private final SqliteDbController db;
    private final BlocksDAO blocksDAO;
    private final BlockchainStateDAO stateDAO;

    public BlockchainDbWriter(BlocksDAO blocksDAO, BlockchainStateDAO stateDAO) {
        this.db = SqliteDbController.getInstance();
        this.blocksDAO = blocksDAO;
        this.stateDAO = stateDAO;
    }

    /**
     * Публичный метод: сам открывает соединение, делает транзакцию и закрывает соединение.
     *
     * @return true если всё записалось успешно, иначе кидает SQLException (или IllegalStateException выше по коду).
     */
    public void appendBlockAndState(
            String login,
            String blockchainName,
            int globalNumber,
            String prevGlobalHashHex,
            byte[] blockBytes,
            BlockchainStateEntry stOrNull,
            String newHashHex
    ) throws SQLException {

        // 1) Открываем соединение (try-with-resources гарантирует закрытие)
        try (Connection c = db.getConnection()) {

            // 2) Включаем ручное управление транзакцией
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);

            try {
                // 3) Внутри одной транзакции:
                //    - вставляем строку блока
                //    - обновляем/создаём blockchain_state
                appendBlockAndState(c, login, blockchainName, globalNumber, prevGlobalHashHex, blockBytes, stOrNull, newHashHex);

                // 4) Фиксируем транзакцию
                c.commit();

            } catch (Exception e) {
                // 5) Если что-то упало — откатываем транзакцию, чтобы не было "полузаписей"
                try { c.rollback(); } catch (SQLException ignore) {}

                // Пробрасываем как SQLException (чтобы вызывающий код мог отдать internal_error и т.п.)
                if (e instanceof SQLException se) throw se;
                throw new SQLException("appendBlockAndState failed", e);

            } finally {
                // 6) Возвращаем autoCommit как было
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) {}
            }
        }
    }

    /**
     * Внутренний/расширенный метод: запись в рамках УЖЕ открытого соединения.
     * Удобно если снаружи хотят объединить несколько действий в одну транзакцию.
     */
    public void appendBlockAndState(
            Connection c,
            String login,
            String blockchainName,
            int globalNumber,
            String prevGlobalHashHex,
            byte[] blockBytes,
            BlockchainStateEntry stOrNull,
            String newHashHex
    ) throws SQLException {

        // A) Вставляем блок (строка в таблицу blocks)
        insertBlockRow(c, login, blockchainName, globalNumber, prevGlobalHashHex, blockBytes);

        // B) Обновляем состояние blockchain_state (создаём если отсутствует)
        BlockchainStateEntry st = stOrNull;
        if (st == null) {
            st = new BlockchainStateEntry();
            st.setBlockchainName(blockchainName);
        }

        // Последний глобальный блок
        st.setLastGlobalNumber(globalNumber);
        st.setLastGlobalHash(newHashHex);

        // Пока линии не используются: lineIndex=0 и lineHash = globalHash
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
            int globalNumber,
            String prevGlobalHashHex,
            byte[] blockBytes
    ) throws SQLException {

        BlockEntry e = new BlockEntry();

        // Кому принадлежит блок (логин владельца цепочки)
        e.setLogin(login);
        e.setBchName(blockchainName);

        // Глобальная нумерация
        e.setBlockGlobalNumber(globalNumber);
        e.setBlockGlobalPreHashe(prevGlobalHashHex);

        // Линии пока не используются: lineIndex=0, lineNumber=globalNumber
        e.setBlockLineIndex(0);
        e.setBlockLineNumber(globalNumber);
        e.setBlockLinePreHashe(prevGlobalHashHex);

        // msgType у тебя пока 0 (при желании позже можно ставить по Body/type)
        e.setMsgType(0);

        // Сырые байты полного блока
        e.setBlockByte(blockBytes);

        // Поля "кому" (для сообщений/трансферов) пока пустые
        e.setToLogin(null);
        e.setToBchName(null);
        e.setToBlockGlobalNumber(null);
        e.setToBlockHashe(null);

        // UPSERT блока
        blocksDAO.upsert(c, e);
    }
}
