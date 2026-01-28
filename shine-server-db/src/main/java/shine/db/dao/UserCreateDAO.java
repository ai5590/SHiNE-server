package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.SolanaUserEntry;

import java.sql.*;

/**
 * UserCreateDAO — атомарное добавление пользователя:
 *  - solana_users (login, blockchain_name, solana_key, blockchain_key, device_key)
 *  - blockchain_state (blockchain_name, login, blockchain_key, size_limit, ... last_block_number=-1 ...)
 *
 * ВАЖНО:
 *  - только INSERT (без перезаписи существующих записей)
 *  - если login или blockchainName заняты — возвращаем false (пользователь уже есть/занято)
 */
public final class UserCreateDAO {

    private static volatile UserCreateDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();
    private final SolanaUsersDAO usersDao = SolanaUsersDAO.getInstance();

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
            String blockchainName,
            String solanaKey,
            String blockchainKey,
            String deviceKey,
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
                // 1) solana_users
                SolanaUserEntry u = new SolanaUserEntry();
                u.setLogin(login);
                u.setBlockchainName(blockchainName);
                u.setSolanaKey(solanaKey);
                u.setBlockchainKey(blockchainKey);
                u.setDeviceKey(deviceKey);

                usersDao.insert(c, u); // если login занят (NOCASE) или blockchainName (unique) -> constraint

                // 2) blockchain_state — строго INSERT, без UPSERT (иначе можно перезаписать существующую цепочку)
                insertBlockchainStateStrict(
                        c,
                        blockchainName,
                        login,
                        blockchainKey,
                        sizeLimit,
                        nowMs
                );

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

    private static void insertBlockchainStateStrict(
            Connection c,
            String blockchainName,
            String login,
            String blockchainKey,
            long sizeLimit,
            long nowMs
    ) throws SQLException {

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
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, blockchainName);
            ps.setString(i++, login);
            ps.setString(i++, blockchainKey);

            ps.setLong(i++, sizeLimit);
            ps.setLong(i++, 0L);

            ps.setInt(i++, -1);
            ps.setNull(i++, Types.BLOB); // старт: блоков ещё нет
            ps.setLong(i++, nowMs);

            ps.executeUpdate(); // если blockchainName занят -> constraint (PK)
        }
    }
}