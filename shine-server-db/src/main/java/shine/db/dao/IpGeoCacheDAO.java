package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.IpGeoCacheEntry;

import java.sql.*;

/**
 * DAO для таблицы ip_geo_cache.
 *
 * Таблица:
 *  - ip            TEXT PRIMARY KEY
 *  - geo           TEXT
 *  - updated_at_ms INTEGER NOT NULL
 *
 * Правило:
 * - методы с Connection НЕ закрывают соединение
 * - методы без Connection сами открывают и закрывают соединение
 */
public final class IpGeoCacheDAO {

    private static volatile IpGeoCacheDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private IpGeoCacheDAO() { }

    public static IpGeoCacheDAO getInstance() {
        if (instance == null) {
            synchronized (IpGeoCacheDAO.class) {
                if (instance == null) instance = new IpGeoCacheDAO();
            }
        }
        return instance;
    }

    // -------------------- UPSERT --------------------

    /** UPSERT с внешним соединением. Соединение НЕ закрывает. */
    public void upsert(Connection c, IpGeoCacheEntry entry) throws SQLException {
        String sql = """
            INSERT INTO ip_geo_cache (ip, geo, updated_at_ms)
            VALUES (?, ?, ?)
            ON CONFLICT(ip)
            DO UPDATE SET
                geo           = excluded.geo,
                updated_at_ms = excluded.updated_at_ms
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entry.getIp());
            ps.setString(2, entry.getGeo());
            ps.setLong(3, entry.getUpdatedAtMs());
            ps.executeUpdate();
        }
    }

    /** UPSERT без внешнего соединения. Сам открывает/закрывает. */
    public void upsert(IpGeoCacheEntry entry) throws SQLException {
        try (Connection c = db.getConnection()) {
            upsert(c, entry);
        }
    }

    // -------------------- SELECT --------------------

    /** Получить по IP с внешним соединением. Соединение НЕ закрывает. */
    public IpGeoCacheEntry getByIp(Connection c, String ip) throws SQLException {
        String sql = """
            SELECT ip, geo, updated_at_ms
            FROM ip_geo_cache
            WHERE ip = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /** Получить по IP без внешнего соединения. Сам открывает/закрывает. */
    public IpGeoCacheEntry getByIp(String ip) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByIp(c, ip);
        }
    }

    // -------------------- DELETE --------------------

    /** Удалить старые записи с внешним соединением. Соединение НЕ закрывает. */
    public int deleteOlderThan(Connection c, long thresholdMs) throws SQLException {
        String sql = "DELETE FROM ip_geo_cache WHERE updated_at_ms < ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, thresholdMs);
            return ps.executeUpdate();
        }
    }

    /** Удалить старые записи без внешнего соединения. Сам открывает/закрывает. */
    public int deleteOlderThan(long thresholdMs) throws SQLException {
        try (Connection c = db.getConnection()) {
            return deleteOlderThan(c, thresholdMs);
        }
    }

    // -------------------- MAPPER --------------------

    private IpGeoCacheEntry mapRow(ResultSet rs) throws SQLException {
        String ip = rs.getString("ip");
        String geo = rs.getString("geo");
        long updatedAtMs = rs.getLong("updated_at_ms");
        return new IpGeoCacheEntry(ip, geo, updatedAtMs);
    }
}