package server.ws;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.JsonInboundProcessor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@WebSocket
public class BlockchainWsEndpoint {
    private static final Logger log = LoggerFactory.getLogger(BlockchainWsEndpoint.class);

    /**
     * Общий пул для обработки ВСЕХ входящих сообщений.
     * Важно: не commonPool, чтобы под нагрузкой всё было предсказуемо.
     */
    private static final ExecutorService WS_EXECUTOR = new ThreadPoolExecutor(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10_000),
            new ThreadFactory() {
                private final AtomicLong n = new AtomicLong(1);
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ws-worker-" + n.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    private Session session;

    /** Контекст для текущего WebSocket-соединения. */
    private final ConnectionContext connectionContext = new ConnectionContext();

    /**
     * Хвост очереди per-session: гарантирует строгую последовательность.
     * Каждое новое сообщение добавляется в цепочку.
     */
    private CompletableFuture<Void> queueTail = CompletableFuture.completedFuture(null);

    /** Защита от гонки при обновлении queueTail. */
    private final Object queueLock = new Object();

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        connectionContext.setWsSession(session);
        log.info("WS connected: {}", session.getRemoteAddress());
    }

    // JSON only
    @OnWebSocketMessage
    public void onText(String message) {
        // Быстро отфильтруем мусор
        if (message == null || message.isBlank()) return;

        // Добавляем обработку в очередь данного соединения (строго по порядку)
        enqueue(() -> processJsonAndReply(message));
    }

    private void enqueue(Runnable task) {
        synchronized (queueLock) {
            queueTail = queueTail.thenRunAsync(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    // Нельзя дать цепочке "сломаться", иначе очередь остановится навсегда
                    log.error("❌ Unhandled error in ws task", t);
                    trySendJsonError();
                }
            }, WS_EXECUTOR);
        }
    }

    private void processJsonAndReply(String message) {
        if (session == null || !session.isOpen()) return;

        log.info("📥 Получено TEXT-сообщение от клиента: {}", message);

        String respJson;
        try {
            respJson = JsonInboundProcessor.processJson(message, connectionContext);
        } catch (Exception ex) {
            log.error("❌ Ошибка при обработке JSON-сообщения", ex);
            trySendJsonError();
            return;
        }

        if (respJson == null) return;
        if (session == null || !session.isOpen()) return;

        log.info("📤 Отправляем ответ клиенту: {}", respJson);

        session.getRemote().sendString(respJson, new WriteCallback() {
            @Override
            public void writeFailed(Throwable x) {
                log.warn("⚠️ Не удалось отправить JSON-ответ клиенту: {}", x.toString());
            }

            @Override
            public void writeSuccess() {
                log.debug("✔ JSON-ответ успешно отправлен");
            }
        });
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        log.info("WS closed: {} {}", statusCode, reason);

        ActiveConnectionsRegistry.getInstance().remove(connectionContext);
        connectionContext.reset();

        // “Обрываем” очередь: новые задачи всё равно не исполнятся из-за проверки session.isOpen(),
        // но можно и явно завершить хвост.
        synchronized (queueLock) {
            queueTail = CompletableFuture.completedFuture(null);
        }

        this.session = null;
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        log.error("WS error", cause);
    }

    private void trySendJsonError() {
        if (session != null && session.isOpen()) {
            String resp = "{\"op\":null,\"requestId\":null,\"status\":500,"
                    + "\"payload\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"Ошибка сервера\"}}";

            session.getRemote().sendString(resp, new WriteCallback() {
                @Override
                public void writeFailed(Throwable x) {
                    log.warn("⚠️ Не удалось отправить JSON-ошибку клиенту: {}", x.toString());
                }

                @Override
                public void writeSuccess() {
                    log.debug("✔ JSON-ошибка успешно отправлена");
                }
            });
        }
    }
}