package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.BlockchainStateEntry;

import java.sql.*;

/**
 * SolanaBlockchainsDAO — таблица блокчейнов пользователя.
 *
 * Сейчас физически это blockchain_state, потому что:
 *  - у одного login может быть несколько blockchainName
 *  - у каждого blockchainName свой blockchainKey и size_limit
 *
 * Правило:
 * - методы с Connection НЕ закрывают соединение
 * - методы без Connection сами открывают и закрывают соединение
 */
public final class SolanaBlockchainsDAO {

    private static volatile SolanaBlockchainsDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();
    private final BlockchainStateDAO stateDao = BlockchainStateDAO.getInstance();

    private SolanaBlockchainsDAO() {}

    public static SolanaBlockchainsDAO getInstance() {
        if (instance == null) {
            synchronized (SolanaBlockchainsDAO.class) {
                if (instance == null) instance = new SolanaBlockchainsDAO();
            }
        }
        return instance;
    }

    public BlockchainStateEntry getByBlockchainName(String blockchainName) throws SQLException {
        return stateDao.getByBlockchainName(blockchainName);
    }

    public BlockchainStateEntry getByBlockchainName(Connection c, String blockchainName) throws SQLException {
        return stateDao.getByBlockchainName(c, blockchainName);
    }

    /** Для HEADER: проверка, что blockchain_state существует и last_global_number=-1. */
    public BlockchainStateEntry requireExistingAtGenesis(Connection c, String blockchainName) throws SQLException {
        return stateDao.requireExistingAtGenesis(c, blockchainName);
    }

    /** Для добавления блока: атомарная проверка лимита + увеличение размера файла. */
    public boolean tryIncreaseFileSizeWithinLimit(Connection c, String blockchainName, long deltaBytes, long nowMs) throws SQLException {
        return stateDao.tryIncreaseFileSizeWithinLimit(c, blockchainName, deltaBytes, nowMs);
    }
}