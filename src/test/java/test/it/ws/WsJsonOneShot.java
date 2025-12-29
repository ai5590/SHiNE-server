package test.it.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import test.it.utils.TestConfig;
import test.it.utils.WsTestClient;

import java.time.Duration;

/**
 * WsJsonOneShot
 *
 * Утилита "отправил JSON -> получил JSON", строго:
 *  - на каждый request создаём НОВОЕ WS соединение
 *  - отправляем
 *  - ждём ответ
 *  - закрываем соединение
 *
 * Важно:
 *  - requestId тут не важен для человека, но важен для WsTestClient, чтобы сопоставить ответ.
 *  - поэтому ставим ВСЕГДА один и тот же requestId (FIXED_REQUEST_ID).
 *  - requestId НЕ логируем.
 */
public final class WsJsonOneShot {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Всегда один и тот же requestId. */
    public static final String FIXED_REQUEST_ID = "it";

    private WsJsonOneShot() {}

    /**
     * Отправить JSON строкой и вернуть JSON ответ строкой.
     * Соединение создаётся и закрывается ВНУТРИ.
     */
    public static String request(String json, Duration timeout) {
        String patched = forceRequestId(json, FIXED_REQUEST_ID);

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            // requestId нам нужен только как ключ ожидания в WsTestClient
            return client.request(FIXED_REQUEST_ID, patched, timeout);
        }
    }

    /**
     * Гарантируем, что requestId есть и равен FIXED_REQUEST_ID.
     * Если JSON кривой — вернём как есть (тогда упадёт выше по логике, и это нормально для теста).
     */
    private static String forceRequestId(String json, String requestId) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!(root instanceof ObjectNode obj)) return json;

            obj.put("requestId", requestId);
            return MAPPER.writeValueAsString(obj);
        } catch (Exception ignore) {
            return json;
        }
    }
}