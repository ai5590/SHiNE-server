package server.logic.ws_protocol.JSON.handlers.blockchain;

import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.BlockEntry;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * BlockchainStateService_new — атомарное добавление блока:
 *  - (опционально) проверки
 *  - вставка строки блока в таблицу blocks
 *  - обновление агрегатного состояния blockchain_state
 *
 * Важно:
 * - всё делается в одной транзакции
 * - DAO-методы с Connection НЕ закрывают соединение
 */
public final class BlockchainStateService_new {

    private static volatile BlockchainStateService_new instance;

    private final SqliteDbController db = SqliteDbController.getInstance();
    private final BlocksDAO blocksDAO = BlocksDAO.getInstance();
    private final BlockchainStateDAO stateDAO = BlockchainStateDAO.getInstance();
    private final SolanaUsersDAO solanaUsersDAO = SolanaUsersDAO.getInstance();

    private BlockchainStateService_new() {}

    public static BlockchainStateService_new getInstance() {
        if (instance == null) {
            synchronized (BlockchainStateService_new.class) {
                if (instance == null) instance = new BlockchainStateService_new();
            }
        }
        return instance;
    }

    /**
     * Атомарно добавляет блок (в рамках одной транзакции).
     *
     * @param login           логин (для поиска loginId)
     * @param blockchainId    id блокчейна
     * @param globalNumber    глобальный номер
     * @param prevGlobalHash  предыдущий глобальный хэш
     * @param blockBytesB64   блок (в Base64) — если у тебя уже byte[], сделай перегрузку
     */
    public void addBlockAtomically(
            String login,
            long blockchainId,
            int globalNumber,
            String prevGlobalHash,
            String blockBytesB64
    ) throws Exception {

        // ⚠️ Тут я не трогаю твою бизнес-логику парсинга blockBytesB64.
        // Просто предполагаю, что у тебя есть метод декодирования.
        byte[] blockBytes = decodeBase64(blockBytesB64);

        try (Connection c = db.getConnection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // 1) получаем loginId по login
                SolanaUserEntry u = solanaUsersDAO.getByLogin(c, login);
                if (u == null) {
                    throw new IllegalStateException("Не найден пользователь в solana_users по login=" + login);
                }
                long loginId = u.getLoginId();

                // 2) вставляем блок в blocks
                insertBlockRow(c, loginId, blockchainId, globalNumber, prevGlobalHash, blockBytes);

                // 3) обновляем агрегатное состояние (если у тебя там отдельная логика — подключи сюда)
                //    Ниже — базовый пример, ты можешь заменить на свои расчёты lineHash/lineNumber и т.д.
                BlockchainStateEntry st = stateDAO.getByBlockchainId(c, blockchainId);
                if (st == null) {
                    throw new IllegalStateException("Не найден blockchain_state для blockchainId=" + blockchainId);
                }

                st.setLastGlobalNumber(globalNumber);
                st.setLastGlobalHash(nn(prevGlobalHash)); // или новый hash, если ты его вычисляешь
                st.setUpdatedAtMs(System.currentTimeMillis());

                stateDAO.upsert(c, st);

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(oldAutoCommit);
            }
        }
    }

    /**
     * Вставка/обновление строки блока в таблицу blocks.
     *
     * Раньше у тебя тут был SQL, который пытался использовать колонку user_login —
     * из-за этого и падало "table blocks has no column named user_login".
     *
     * Теперь всё делаем через BlocksDAO, где имена колонок гарантированно совпадают со схемой.
     */
    private void insertBlockRow(
            Connection c,
            long loginId,
            long blockchainId,
            int globalNumber,
            String prevGlobalHash,
            byte[] blockBytes
    ) throws SQLException {

        BlockEntry e = new BlockEntry();
        e.setLoginId(loginId);
        e.setBlockchainId(blockchainId);

        e.setBlockGlobalNumber(globalNumber);
        e.setBlockGlobalPreHashe(nn(prevGlobalHash));

        // ⚠️ Эти поля (линии/типы/маршрутизация) заполни так, как у тебя реально устроен блок.
        // Я ставлю дефолты, чтобы код компилился и логика была ясна.
        e.setBlockLineIndex(0);
        e.setBlockLineNumber(0);
        e.setBlockLinePreHashe("");

        e.setMsgType(0);

        e.setBlockByte(blockBytes);

        e.setToLoginId(0);
        e.setToBlockchainId(0);
        e.setToBlockGlobalNumber(0);
        e.setToBlockHashe("");

        // upsert — безопаснее, чем insert, если возможны повторы при ретраях
        blocksDAO.upsert(c, e);
    }

    // -------------------- utils --------------------

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static byte[] decodeBase64(String s) {
        if (s == null || s.isBlank()) return null;
        return java.util.Base64.getDecoder().decode(s);
    }
}