package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;

import java.sql.*;

/**
 * UserCreateDAO — атомарное добавление пользователя:
 *  - solana_users (login, device_key)
 *  - blockchain_state (blockchain_name, login, blockchain_key, size_limit, ... last_global_number=-1 ...)
 *
 * ВАЖНО:
 *  - только INSERT
 *  - если login или blockchainName заняты — возвращаем false (пользователь уже есть/занято)
 */
public final class UserCreateDAO {

    private static volatile UserCreateDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();
    private final SolanaUsersDAO usersDao = SolanaUsersDAO.getInstance();
    private final BlockchainStateDAO stateDao = BlockchainStateDAO.getInstance();

    private UserCreateDAO() {}

    public static UserCreateDAO getInstance() {
        if (instance == null) {
            synchronized (UserCreateDAO.class) {
                if (instance == null) instance = new UserCreateDAO();
            }
        }
        return instance;
    }

    /**
     * @return true если добавили; false если занято (login уже есть или blockchainName уже существует).
     */
    public boolean insertUserWithBlockchain(
            String login,
            String deviceKey,
            String blockchainName,
            String blockchainKey,
            long sizeLimit,
            long nowMs
    ) throws SQLException {

        try (Connection c = db.getConnection()) {
            boolean oldAuto = c.getAutoCommit();
            c.setAutoCommit(false);

            // BEGIN IMMEDIATE — чтобы сразу взять write-lock и не ловить гонки
            try (Statement st = c.createStatement()) {
                st.execute("BEGIN IMMEDIATE");
            }

            try {
                // 1) user
                SolanaUserEntry u = new SolanaUserEntry(login, deviceKey, deviceKey);
                usersDao.insert(c, u); // если login занят -> constraint

                // 2) blockchain_state
                BlockchainStateEntry st = new BlockchainStateEntry();
                st.setBlockchainName(blockchainName);
                st.setLogin(login);
                st.setBlockchainKey(blockchainKey);
                st.setSizeLimit(sizeLimit);
                st.setFileSizeBytes(0L);

                // старт: глобальных блоков ещё нет
                st.setLastGlobalNumber(-1);
                st.setLastGlobalHash("");

                for (int line = 0; line < 8; line++) {
                    st.setLastLineNumber(line, 0);
                    st.setLastLineHash(line, "");
                }

                st.setUpdatedAtMs(nowMs);

                stateDao.upsert(c, st); // если blockchainName занят -> constraint (PK)

                c.commit();
                return true;

            } catch (SQLException e) {
                c.rollback();

                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                if (msg.contains("constraint")) {
                    return false;
                }
                throw e;

            } finally {
                c.setAutoCommit(oldAuto);
            }
        }
    }
}