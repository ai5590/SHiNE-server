package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.UserParamEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * UserParamsDAO — хранение сохранённых параметров пользователя.
 *
 * Правило:
 * - методы с Connection НЕ закрывают соединение
 * - методы без Connection сами открывают и закрывают соединение
 *
 * ЛОГИКА time_ms:
 * - БД принимает запись только если она "новее" (time_ms строго больше текущего).
 * - Реализовано атомарно одним SQL: UPSERT + WHERE users_params.time_ms < excluded.time_ms
 */
public final class UserParamsDAO {

    private static volatile UserParamsDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private UserParamsDAO() { }

    public static UserParamsDAO getInstance() {
        if (instance == null) {
            synchronized (UserParamsDAO.class) {
                if (instance == null) instance = new UserParamsDAO();
            }
        }
        return instance;
    }

    // -------------------- UPSERT (IF NEWER) --------------------

    public int upsertIfNewer(Connection c, UserParamEntry e) throws SQLException {
        String sql = """
            INSERT INTO users_params (
                login,
                param,
                time_ms,
                value,
                device_key,
                signature
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(login, param)
            DO UPDATE SET
                time_ms    = excluded.time_ms,
                value      = excluded.value,
                device_key = excluded.device_key,
                signature  = excluded.signature
            WHERE users_params.time_ms < excluded.time_ms
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.getLogin());
            ps.setString(2, e.getParam());
            ps.setLong(3, e.getTimeMs());
            ps.setString(4, e.getValue());

            if (e.getDeviceKey() != null) ps.setString(5, e.getDeviceKey());
            else ps.setNull(5, Types.VARCHAR);

            if (e.getSignature() != null) ps.setString(6, e.getSignature());
            else ps.setNull(6, Types.VARCHAR);

            return ps.executeUpdate();
        }
    }

    public int upsertIfNewer(UserParamEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            return upsertIfNewer(c, e);
        }
    }

    // -------------------- SELECT --------------------

    public UserParamEntry getByLoginAndParam(Connection c, String login, String param) throws SQLException {
        String sql = """
            SELECT
                login,
                param,
                time_ms,
                value,
                device_key,
                signature
            FROM users_params
            WHERE login = ? AND param = ?
            LIMIT 1
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, param);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    public UserParamEntry getByLoginAndParam(String login, String param) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByLoginAndParam(c, login, param);
        }
    }

    public List<UserParamEntry> getByLogin(Connection c, String login) throws SQLException {
        String sql = """
            SELECT
                login,
                param,
                time_ms,
                value,
                device_key,
                signature
            FROM users_params
            WHERE login = ?
            ORDER BY time_ms DESC
            """;

        List<UserParamEntry> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<UserParamEntry> getByLogin(String login) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByLogin(c, login);
        }
    }

    // -------------------- MAPPER --------------------

    private static UserParamEntry mapRow(ResultSet rs) throws SQLException {
        UserParamEntry e = new UserParamEntry();
        e.setLogin(rs.getString("login"));
        e.setParam(rs.getString("param"));
        e.setTimeMs(rs.getLong("time_ms"));
        e.setValue(rs.getString("value"));

        String dk = rs.getString("device_key");
        if (rs.wasNull()) dk = null;
        e.setDeviceKey(dk);

        String sig = rs.getString("signature");
        if (rs.wasNull()) sig = null;
        e.setSignature(sig);

        return e;
    }
}