package test.it.utils;

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
}