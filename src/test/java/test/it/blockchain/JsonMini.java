package test.it.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JsonMini — маленькие утилиты, чтобы не раздувать зависимости.
 */
final class JsonMini {
    private static final ObjectMapper M = new ObjectMapper();
    private JsonMini() {}

    static String extractPayloadString(String json, String field) {
        try {
            JsonNode root = M.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has(field)) {
                JsonNode v = payload.get(field);
                return (v == null || v.isNull()) ? null : v.asText();
            }
        } catch (Exception ignore) {}
        return null;
    }
}