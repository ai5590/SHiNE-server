package server.logic.ws_protocol.JSON.handlers.blockchain.Net_AddBlock_Handler_utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class BlockchainLocks {
    private static final ConcurrentHashMap<String, ReentrantLock> MAP = new ConcurrentHashMap<>();

    private BlockchainLocks() {}

    public static ReentrantLock lockFor(String blockchainName) {
        return MAP.computeIfAbsent(blockchainName, id -> new ReentrantLock(true)); // fair=true
    }
}