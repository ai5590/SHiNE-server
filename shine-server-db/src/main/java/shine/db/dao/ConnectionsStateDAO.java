package shine.db.dao;

import shine.db.SqliteDbController;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ConnectionsStateDAO — чтение текущего состояния связей из connections_state.
 *
 * ВАЖНО:
 * - login в запросах может быть в любом регистре, поэтому в WHERE используем COLLATE NOCASE
 * - в ответах возвращаем логины в каноническом регистре через JOIN на solana_users
 *
 * ПРИМЕЧАНИЕ:
 * Таблица пользователей тут названа "solana_users". Если у тебя иначе — поменяй в SQL.
 */
public final class ConnectionsStateDAO {

    private static volatile ConnectionsStateDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private ConnectionsStateDAO() {}

    public static ConnectionsStateDAO getInstance() {
        if (instance == null) {
            synchronized (ConnectionsStateDAO.class) {
                if (instance == null) instance = new ConnectionsStateDAO();
            }
        }
        return instance;
    }

    /**
     * Outgoing: список логинов (канонических), кому login поставил relType.
     */
    public List<String> listOutgoingByRelTypeCanonical(Connection c, String loginAnyCase, int relType) throws SQLException {
        String sql = """
            SELECT u.login AS friend_login
            FROM connections_state cs
            JOIN solana_users u
              ON u.login = cs.to_login COLLATE NOCASE
            WHERE cs.login = ? COLLATE NOCASE
              AND cs.rel_type = ?
            ORDER BY u.login
            """;

        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, loginAnyCase);
            ps.setInt(2, relType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString("friend_login");
                    if (v != null) out.add(v);
                }
            }
        }
        return out;
    }

    /**
     * Incoming: список логинов (канонических), кто поставил relType пользователю login.
     */
    public List<String> listIncomingByRelTypeCanonical(Connection c, String loginAnyCase, int relType) throws SQLException {
        String sql = """
            SELECT u.login AS friend_login
            FROM connections_state cs
            JOIN solana_users u
              ON u.login = cs.login COLLATE NOCASE
            WHERE cs.to_login = ? COLLATE NOCASE
              AND cs.rel_type = ?
            ORDER BY u.login
            """;

        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, loginAnyCase);
            ps.setInt(2, relType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString("friend_login");
                    if (v != null) out.add(v);
                }
            }
        }
        return out;
    }

    /**
     * Mutual: список логинов (канонических), у кого дружба в обе стороны.
     */
    public List<String> listMutualByRelTypeCanonical(Connection c, String loginAnyCase, int relType) throws SQLException {
        String sql = """
            SELECT u.login AS friend_login
            FROM connections_state a
            JOIN solana_users u
              ON u.login = a.to_login COLLATE NOCASE
            WHERE a.login = ? COLLATE NOCASE
              AND a.rel_type = ?
              AND EXISTS (
                SELECT 1
                FROM connections_state b
                WHERE b.login = a.to_login COLLATE NOCASE
                  AND b.to_login = a.login COLLATE NOCASE
                  AND b.rel_type = a.rel_type
              )
            ORDER BY u.login
            """;

        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, loginAnyCase);
            ps.setInt(2, relType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString("friend_login");
                    if (v != null) out.add(v);
                }
            }
        }
        return out;
    }

    public void upsertRelation(Connection c,
                               String login,
                               int relType,
                               String toLogin,
                               String toBchName,
                               Integer toBlockNumber,
                               byte[] toBlockHash) throws SQLException {
        String sql = """
            INSERT INTO connections_state (login, rel_type, to_login, to_bch_name, to_block_number, to_block_hash)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(login, rel_type, to_login) DO UPDATE SET
                to_bch_name=excluded.to_bch_name,
                to_block_number=excluded.to_block_number,
                to_block_hash=excluded.to_block_hash
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setInt(2, relType);
            ps.setString(3, toLogin);
            ps.setString(4, toBchName);
            if (toBlockNumber == null) ps.setNull(5, java.sql.Types.INTEGER); else ps.setInt(5, toBlockNumber);
            ps.setBytes(6, toBlockHash);
            ps.executeUpdate();
        }
    }

}
