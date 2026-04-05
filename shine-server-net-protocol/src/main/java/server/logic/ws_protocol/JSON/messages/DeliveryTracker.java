package server.logic.ws_protocol.JSON.messages;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class DeliveryTracker {
    private static final DeliveryTracker INSTANCE = new DeliveryTracker();

    public static DeliveryTracker getInstance() { return INSTANCE; }

    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> waiters = new ConcurrentHashMap<>();

    private DeliveryTracker() {}

    public CompletableFuture<Boolean> register(String eventId) {
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        waiters.put(eventId, f);
        return f;
    }

    public void ack(String eventId) {
        CompletableFuture<Boolean> f = waiters.remove(eventId);
        if (f != null) f.complete(true);
    }

    public void fail(String eventId) {
        CompletableFuture<Boolean> f = waiters.remove(eventId);
        if (f != null) f.complete(false);
    }

    public void remove(String eventId) {
        waiters.remove(eventId);
    }
}
