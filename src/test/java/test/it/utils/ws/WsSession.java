package test.it.utils.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import test.it.utils.TestConfig;
import test.it.utils.log.TestLog;

import java.time.Duration;

/**
 * WsSession — одно WS соединение на много запросов.
 *
 * Использование в тесте:
 * try (WsSession ws = WsSession.open()) {
 *   String resp = ws.call("AuthChallenge", JsonBuilders.authChallenge(login), t);
 * }
 */
public final class WsSession implements AutoCloseable {

    private static final ObjectMapper M = new ObjectMapper();

    private final WsTestClient client;

    private WsSession(WsTestClient client) {
        this.client = client;
    }

    public static WsSession open() {
        return new WsSession(new WsTestClient(TestConfig.WS_URI));
    }

    /** Отправить JSON (в котором уже есть requestId) и получить JSON ответ строкой. */
    public String call(String op, String requestJson, Duration timeout) {
        String requestId = extractRequestId(requestJson);
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestJson must contain requestId: " + requestJson);

        if (TestConfig.DEBUG()) TestLog.send(op, requestJson);

        String resp = client.request(requestId, requestJson, timeout);

        if (TestConfig.DEBUG()) TestLog.recv(op, resp);

        return resp;
    }

    private static String extractRequestId(String json) {
        try {
            JsonNode root = M.readTree(json);
            JsonNode id = root.get("requestId");
            return (id == null || id.isNull()) ? null : id.asText();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        client.close();
    }
}