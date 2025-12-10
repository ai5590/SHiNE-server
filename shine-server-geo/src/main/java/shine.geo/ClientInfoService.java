package shine.geo;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

public final class ClientInfoService {

    public static final String CLIENT_INFO_UNKNOWN = "";
    public static final String LANGUAGE_UNKNOWN = "";

    private ClientInfoService() { }

    /**
     * Собирает строку с информацией о клиенте:
     * User-Agent, client-hints и удалённый IP.
     */
    public static String buildClientInfoString(Session wsSession) {
        if (wsSession == null) {
            return CLIENT_INFO_UNKNOWN;
        }

        UpgradeRequest req = wsSession.getUpgradeRequest();
        if (req == null) {
            return CLIENT_INFO_UNKNOWN;
        }

        String userAgent     = getFirstHeader(req, "User-Agent");
        String secChUa       = getFirstHeader(req, "Sec-CH-UA");
        String secChPlatform = getFirstHeader(req, "Sec-CH-UA-Platform");
        String secChMobile   = getFirstHeader(req, "Sec-CH-UA-Mobile");

        // IP берём через общий метод, чтобы не дублировать логику
        String remoteIp = extractClientIp(wsSession);

        StringBuilder sb = new StringBuilder();

        if (userAgent != null) {
            sb.append("UA=").append(userAgent);
        }
        if (secChUa != null) {
            appendSep(sb);
            sb.append("chUa=").append(secChUa);
        }
        if (secChPlatform != null) {
            appendSep(sb);
            sb.append("platform=").append(secChPlatform);
        }
        if (secChMobile != null) {
            appendSep(sb);
            sb.append("mobile=").append(secChMobile);
        }
        if (remoteIp != null && !remoteIp.isEmpty()) {
            appendSep(sb);
            sb.append("remote=").append(remoteIp);
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? CLIENT_INFO_UNKNOWN : result;
    }

    /**
     * Пытается вытащить реальный IP клиента из WebSocket-сессии.
     *
     * Приоритет:
     *  1) X-Forwarded-For (если стоим за прокси / балансировщиком)
     *  2) X-Real-IP
     *  3) remoteAddress из WebSocket-сессии
     */
    public static String extractClientIp(Session wsSession) {
        if (wsSession == null) {
            return null;
        }

        UpgradeRequest req = wsSession.getUpgradeRequest();

        // 1) X-Forwarded-For: может быть список IP через запятую
        if (req != null) {
            String xff = getFirstHeader(req, "X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String first = xff.split(",")[0].trim();
                if (!first.isBlank()) {
                    return first;
                }
            }

            // 2) X-Real-IP
            String xRealIp = getFirstHeader(req, "X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }

        // 3) fallback: remoteAddress из WebSocket-сессии
        SocketAddress rawAddr = wsSession.getRemoteAddress();
        if (rawAddr instanceof InetSocketAddress inet) {
            if (inet.getAddress() != null) {
                return inet.getAddress().getHostAddress();
            }
        }

        return null;
    }

    public static String extractPreferredLanguageTag(Session wsSession) {
        if (wsSession == null) {
            return LANGUAGE_UNKNOWN;
        }

        UpgradeRequest req = wsSession.getUpgradeRequest();
        if (req == null) {
            return LANGUAGE_UNKNOWN;
        }

        String acceptLanguage = getFirstHeader(req, "Accept-Language");
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return LANGUAGE_UNKNOWN;
        }

        String first = acceptLanguage.split(",")[0].trim();
        if (first.isEmpty()) {
            return LANGUAGE_UNKNOWN;
        }

        String[] parts = first.split(";");
        String tag = parts[0].trim();

        return tag.isEmpty() ? LANGUAGE_UNKNOWN : tag;
    }

    private static String getFirstHeader(UpgradeRequest req, String headerName) {
        if (req == null || headerName == null) return null;
        List<String> values = req.getHeaders().get(headerName);
        if (values == null || values.isEmpty()) return null;
        String v = values.get(0);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static void appendSep(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append("; ");
        }
        // если строка пустая — разделитель не нужен
    }
}