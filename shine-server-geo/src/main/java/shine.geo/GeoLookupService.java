package shine.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Сервис для геолокации по IP.
 * .
 * Основной метод:
 *   resolveCountryCityOrIp(ip) -> "Country, City" или GEO_UNKNOWN, если не удалось.
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

            // Собираем строку
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