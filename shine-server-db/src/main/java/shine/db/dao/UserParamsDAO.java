package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.UserParamEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Здесь храним сохранённые параметры пользователей (в основном до какого сообщения просмотрены ленты) */
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

    // -------------------- UPSERT --------------------

    /** UPSERT с внешним соединением. Соединение НЕ закрывает. */
    public void upsert(Connection c, UserParamEntry param) throws SQLException {
        String sql = """
            INSERT INTO users_params (
                login,
                param,
                bch_channel_id,
                value,
                time_ms,
                pubkey_num,
                signature
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(login, param)
            DO UPDATE SET
                bch_channel_id = excluded.bch_channel_id,
                value          = excluded.value,
                time_ms        = excluded.time_ms,
                pubkey_num     = excluded.pubkey_num,
                signature      = excluded.signature
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param.getLogin());
            ps.setString(2, param.getParam());
            ps.setLong(3, param.getBchChannelId());
            ps.setString(4, param.getValue());
            ps.setLong(5, param.getTimeMs());
            ps.setInt(6, param.getPubkeyNum());
            ps.setString(7, param.getSignature());
            ps.executeUpdate();
        }
    }

    /** UPSERT без внешнего соединения. Сам открывает/закрывает. */
    public void upsert(UserParamEntry param) throws SQLException {
        try (Connection c = db.getConnection()) {
            upsert(c, param);
        }
    }

    // -------------------- SELECT --------------------

    /** Получить параметр с внешним соединением. Соединение НЕ закрывает. */
    public UserParamEntry getByUserLoginAndParam(Connection c, String login, String paramName) throws SQLException {
        String sql = """
            SELECT
                login,
                param,
                bch_channel_id,
                value,
                time_ms,
                pubkey_num,
                signature
            FROM users_params
            WHERE login = ? AND param = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, paramName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /** Получить параметр без внешнего соединения. Сам открывает/закрывает. */
    public UserParamEntry getByUserLoginAndParam(String login, String paramName) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByUserLoginAndParam(c, login, paramName);
        }
    }

    /** Получить все параметры пользователя с внешним соединением. Соединение НЕ закрывает. */
    public List<UserParamEntry> getByUserLogin(Connection c, String login) throws SQLException {
        String sql = """
            SELECT
                login,
                param,
                bch_channel_id,
                value,
                time_ms,
                pubkey_num,
                signature
            FROM users_params
            WHERE login = ?
            ORDER BY time_ms DESC
            """;

        List<UserParamEntry> result = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }

        return result;
    }

    /** Получить все параметры пользователя без внешнего соединения. Сам открывает/закрывает. */
    public List<UserParamEntry> getByUserLogin(String login) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByUserLogin(c, login);
        }
    }

    // -------------------- MAPPER --------------------

    private UserParamEntry mapRow(ResultSet rs) throws SQLException {
        return new UserParamEntry(
                rs.getString("login"),
                rs.getString("param"),
                rs.getLong("bch_channel_id"),
                rs.getString("value"),
                rs.getLong("time_ms"),
                (short) rs.getInt("pubkey_num"),
                rs.getString("signature")
        );
    }
}