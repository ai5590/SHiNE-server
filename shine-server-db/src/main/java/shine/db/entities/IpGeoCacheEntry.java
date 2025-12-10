package shine.db.entities;

/**
 * Запись в таблице ip_geo_cache.
 *
 * Храним:
 *  - ip            — строка IP-адреса (PRIMARY KEY)
 *  - geo           — строка "Country, City" или любое текстовое описание
 *  - updatedAtMs   — время последнего обновления (Unix time в мс)
 */
public class IpGeoCacheEntry {

    private String ip;
    private String geo;
    private long updatedAtMs;

    public IpGeoCacheEntry() {
    }

    public IpGeoCacheEntry(String ip, String geo, long updatedAtMs) {
        this.ip = ip;
        this.geo = geo;
        this.updatedAtMs = updatedAtMs;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getGeo() {
        return geo;
    }

    public void setGeo(String geo) {
        this.geo = geo;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public void setUpdatedAtMs(long updatedAtMs) {
        this.updatedAtMs = updatedAtMs;
    }
}