package shine.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import shine.db.dao.IpGeoCacheDAO;
import shine.db.entities.IpGeoCacheEntry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;

/**
 * Сервис для геолокации по IP.
 *
 * Основной метод без кэша:
 *   resolveCountryCityOrIp(ip) -> "Country, City" или GEO_UNKNOWN
 *
 * Метод с кэшированием в БД:
 *   resolveCountryCityOrIpWithCache(ip) -> сначала смотрит в ip_geo_cache,
 *   при отсутствии записи — обращается к внешнему сервису, сохраняет результат в кэш и возвращает его.
 */
public final class GeoLookupService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // Константа — что возвращать, если геолокация недоступна
    public static final String GEO_UNKNOWN = "unknown";

    // Сервис геолокации (потом можно вынести в конфиг)
    private static final String GEO_API_URL = "http://ip-api.com/json/";

    // Сервис для получения собственного внешнего IP
    private static final String PUBLIC_IP_URL = "https://api.ipify.org";

    private GeoLookupService() {
        // utility-класс
    }

    /**
     * ВАРИАНТ БЕЗ КЭША.
     *
     * Возвращает строку вида "Country, City" по IP.
     * Если запрос не удался, возвращает GEO_UNKNOWN.
     */
    public static String resolveCountryCityOrIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return GEO_UNKNOWN;
        }

        // Приватные/локальные IP — геолокация невозможна
        if (isPrivateOrLocalIp(ip)) {
            return GEO_UNKNOWN;
        }

        try {
            String url = GEO_API_URL + ip + "?fields=status,country,city,message";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                return GEO_UNKNOWN;
            }

            JsonNode root = JSON_MAPPER.readTree(response.body());
            String status = root.path("status").asText();

            if (!"success".equals(status)) {
                // "fail", "private range", "quota exceeded", и т.д.
                return GEO_UNKNOWN;
            }

            String country = root.path("country").asText(null);
            String city = root.path("city").asText(null);

            if (country == null && city == null) {
                return GEO_UNKNOWN;
            }

            if (country != null && city != null) {
                return country + ", " + city;
            } else if (country != null) {
                return country;
            } else {
                return city;
            }

        } catch (IOException | InterruptedException e) {
            // Ошибки сети — возвращаем unknown
            return GEO_UNKNOWN;
        }
    }

    /**
     * ВАРИАНТ С КЭШЕМ В БАЗЕ (ip_geo_cache).
     *
     * Логика:
     *  1) Если IP пустой или локальный — сразу GEO_UNKNOWN (и ничего не пишем в кэш).
     *  2) Пытаемся найти ip в ip_geo_cache:
     *       - если нашли — возвращаем geo из записи.
     *  3) Если не нашли — вызываем resolveCountryCityOrIp(ip) (внешний сервис),
     *       - результат (включая GEO_UNKNOWN) сохраняем в ip_geo_cache через IpGeoCacheDAO.upsert()
     *       - возвращаем сохранённый результат.
     *
     * В случае ошибок БД — просто падаем назад на поведение без кэша.
     */
    public static String resolveCountryCityOrIpWithCache(String ip) {
        if (ip == null || ip.isBlank()) {
            return GEO_UNKNOWN;
        }

        // Приватные/локальные IP не кешируем и не запрашиваем
        if (isPrivateOrLocalIp(ip)) {
            return GEO_UNKNOWN;
        }

        // 1. Сначала пробуем взять из кэша
        IpGeoCacheDAO dao = IpGeoCacheDAO.getInstance();
        try {
            IpGeoCacheEntry cached = dao.getByIp(ip);
            if (cached != null) {
                String geo = cached.getGeo();
                if (geo != null && !geo.isBlank()) {
                    return geo;
                }
                // Если geo пустая строка (на всякий случай) — идём за свежими данными.
            }
        } catch (SQLException e) {
            // Ошибка БД — логируем при желании и продолжаем без кэша
            // log.warn("Failed to read IP geo cache", e);
        }

        // 2. Вызываем "сырой" метод, который ходит во внешний сервис
        String resolvedGeo = resolveCountryCityOrIp(ip);

        // 3. Пишем результат в кэш (включая GEO_UNKNOWN)
        try {
            IpGeoCacheEntry entry = new IpGeoCacheEntry(
                    ip,
                    resolvedGeo,
                    System.currentTimeMillis()
            );
            dao.upsert(entry);
        } catch (SQLException e) {
            // Ошибка БД при записи — просто игнорируем, кэш не обязателен для работы
            // log.warn("Failed to upsert IP geo cache", e);
        }

        return resolvedGeo;
    }

    /**
     * Пытается получить внешний IP текущей машины через HTTP-сервис.
     * В случае ошибки возвращает fallbackIp.
     */
    public static String fetchPublicIpOrDefault(String fallbackIp) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PUBLIC_IP_URL))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                return fallbackIp;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return fallbackIp;
            }

            return body.trim();

        } catch (IOException | InterruptedException e) {
            return fallbackIp;
        }
    }

    /**
     * Проверка на частные/локальные IP.
     */
    private static boolean isPrivateOrLocalIp(String ip) {
        ip = ip.trim();

        return ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("127.")
                || ip.startsWith("0.")
                || ip.startsWith("169.254.")
                // Диапазон 172.16.0.0 – 172.31.255.255
                || ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }
}