package test.it.utils.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class JsonParsers {
    private JsonParsers(){}
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static int status(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            return root.has("status") ? root.get("status").asInt() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String authNonce(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("authNonce")) return payload.get("authNonce").asText();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** nonce из SessionChallenge(v2) */
    public static String sessionNonce(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("nonce")) return payload.get("nonce").asText();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String sessionId(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("sessionId")) return payload.get("sessionId").asText();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // оставляю для совместимости с другими тестами, но в IT_02(v2) больше не используется
    public static String sessionPwd(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("sessionPwd")) return payload.get("sessionPwd").asText();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String storagePwd(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("storagePwd")) return payload.get("storagePwd").asText();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> sessionIds(String json) {
        List<String> res = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload == null) return res;
            JsonNode arr = payload.get("sessions");
            if (arr == null || !arr.isArray()) return res;

            for (JsonNode s : arr) {
                JsonNode id = s.get("sessionId");
                if (id != null && !id.isNull()) res.add(id.asText());
            }
        } catch (Exception ignored) {}
        return res;
    }

    public static String errorCode(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);

            // поддержка старого формата (верхний уровень)
            if (root.has("errorCode")) return root.get("errorCode").asText();
            // поддержка нового формата (верхний уровень)
            if (root.has("code")) return root.get("code").asText();

            JsonNode payload = root.get("payload");
            if (payload != null) {
                // поддержка старого формата (внутри payload)
                if (payload.has("errorCode")) return payload.get("errorCode").asText();
                // поддержка нового формата (внутри payload)
                if (payload.has("code")) return payload.get("code").asText();
            }
        } catch (Exception ignored) {}

        return null;
    }

    // ---------------- GetUser helpers ----------------

    public static Boolean exists(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("exists")) return payload.get("exists").asBoolean();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String userLogin(String json) {
        return getPayloadText(json, "login");
    }

    public static String userBlockchainName(String json) {
        return getPayloadText(json, "blockchainName");
    }

    public static String userSolanaKey(String json) {
        return getPayloadText(json, "solanaKey");
    }

    public static String userBlockchainKey(String json) {
        return getPayloadText(json, "blockchainKey");
    }

    public static String userDeviceKey(String json) {
        return getPayloadText(json, "deviceKey");
    }

    private static String getPayloadText(String json, String field) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has(field) && !payload.get(field).isNull()) {
                return payload.get(field).asText();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}