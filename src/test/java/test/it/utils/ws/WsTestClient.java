package test.it.utils.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

public final class WsTestClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebSocket ws;
    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public WsTestClient(String wsUri) {
        HttpClient client = HttpClient.newHttpClient();
        this.ws = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(wsUri), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String msg = data.toString();
                        String requestId = extractRequestId(msg);
                        if (requestId != null) {
                            CompletableFuture<String> f = pending.remove(requestId);
                            if (f != null) f.complete(msg);
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        // Завалим все ожидания, чтобы тест корректно упал
                        pending.forEach((k, f) -> f.completeExceptionally(error));
                        pending.clear();
                    }
                }).join();

        this.ws.request(1);
    }

    public String request(String requestId, String json, Duration timeout) {
        CompletableFuture<String> fut = new CompletableFuture<>();
        pending.put(requestId, fut);
        ws.sendText(json, true);
        try {
            return fut.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            pending.remove(requestId);
            throw new RuntimeException("Timeout/Fail waiting response requestId=" + requestId, e);
        }
    }

    private static String extractRequestId(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode id = root.get("requestId");
            return id != null && !id.isNull() ? id.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void close() {
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        } catch (Exception ignored) {}
    }
}