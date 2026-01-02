package test.it.addBlockUtils;

// старый класс

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * WsJsonRoundtripClient
 *
 * Один запрос = одно соединение:
 *  - открыл WS
 *  - отправил JSON (text frame)
 *  - дождался первого ответа TEXT
 *  - закрыл WS
 *
 * Здесь requestId НЕ используется вообще (ни для ожидания, ни для логов).
 * Просто возвращаем первый пришедший ответ как строку JSON.
 */
public final class WsJsonRoundtripClient {

    private WsJsonRoundtripClient() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String sendOnce(String wsUri, String requestJson, Duration timeout) {
        HttpClient client = HttpClient.newHttpClient();

        CompletableFuture<String> firstMessage = new CompletableFuture<>();

        WebSocket ws = client.newWebSocketBuilder()
                .connectTimeout(timeout)
                .buildAsync(URI.create(wsUri), new WebSocket.Listener() {

                    private final StringBuilder buf = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buf.append(data);
                        if (last) {
                            String msg = buf.toString();
                            buf.setLength(0);
                            if (!firstMessage.isDone()) firstMessage.complete(msg);
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        if (!firstMessage.isDone()) firstMessage.completeExceptionally(error);
                    }
                }).join();

        // отправляем
        ws.sendText(requestJson, true).join();

        // ждём
        String resp;
        try {
            resp = firstMessage.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            try { ws.abort(); } catch (Exception ignored) {}
            throw new RuntimeException("Timeout/Fail waiting response (single-shot WS). uri=" + wsUri, e);
        }

        // закрываем
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
        } catch (Exception ignored) {}

        return resp;
    }

    /** Утилита: прочитать status из ответа (если нужно быстро проверить). */
    public static int status(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            return root.has("status") ? root.get("status").asInt() : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}