package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.UserParamEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Здесь зраним сохранённые параметры пользователей (в основном до каково сообщения просмотрены ленты) */
public final class UserParamsDAO {

    private static volatile UserParamsDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private UserParamsDAO() {
    }

    public static UserParamsDAO getInstance() {
        if (instance == null) {
            synchronized (UserParamsDAO.class) {
                if (instance == null) {
                    instance = new UserParamsDAO();
                }
            }
        }
        return instance;
    }

    /**
     * UPSERT методом ON CONFLICT — одним SQL-запросом.
     * Если запись существует -> обновляем поля.
     * Если нет -> вставляем новую запись.
     */
    public void upsert(UserParamEntry param) throws SQLException {
        String sql = """
            INSERT INTO users_params (
                loginId,
                param,
                bch_channel_id,
                value,
                time_ms,
                pubkey_num,
                signature
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(loginId, param)
            DO UPDATE SET
                bch_channel_id = excluded.bch_channel_id,
                value          = excluded.value,
                time_ms        = excluded.time_ms,
                pubkey_num     = excluded.pubkey_num,
                signature      = excluded.signature
            """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, param.getLoginId());
            ps.setString(2, param.getParam());
            ps.setLong(3, param.getBchChannelId());
            ps.setString(4, param.getValue());
            ps.setLong(5, param.getTimeMs());
            ps.setInt(6, param.getPubkeyNum());
            ps.setString(7, param.getSignature());
            ps.executeUpdate();
        }
    }

    /**
     * Получить параметр по loginId + param.
     */
    public UserParamEntry getByUserIdAndParam(long loginId, String paramName) throws SQLException {
        String sql = """
            SELECT
                loginId,
                param,
                bch_channel_id,
                value,
                time_ms,
                pubkey_num,
                signature
            FROM users_params
            WHERE loginId = ? AND param = ?
            """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, loginId);
            ps.setString(2, paramName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /**
     * Получить все параметры пользователя.
     */
    public List<UserParamEntry> getByUserId(long loginId) throws SQLException {
        String sql = """
            SELECT
                loginId,
                param,
                bch_channel_id,
                value,
                time_ms,
                pubkey_num,
                signature
            FROM users_params
            WHERE loginId = ?
            ORDER BY time_ms DESC
            """;

        List<UserParamEntry> result = new ArrayList<>();

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, loginId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }

        return result;
    }

    private UserParamEntry mapRow(ResultSet rs) throws SQLException {
        return new UserParamEntry(
                rs.getLong("loginId"),
                rs.getString("param"),
                rs.getLong("bch_channel_id"),
                rs.getString("value"),
                rs.getLong("time_ms"),
                (short) rs.getInt("pubkey_num"),
                rs.getString("signature")
        );
    }
}