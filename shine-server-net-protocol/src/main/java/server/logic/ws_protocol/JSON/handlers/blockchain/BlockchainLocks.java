package server.logic.ws_protocol.JSON.handlers.blockchain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class BlockchainLocks {
    private static final ConcurrentHashMap<Long, ReentrantLock> MAP = new ConcurrentHashMap<>();

    private BlockchainLocks() {}

    public static ReentrantLock lockFor(long blockchainId) {
        return MAP.computeIfAbsent(blockchainId, id -> new ReentrantLock(true)); // fair=true
    }
}