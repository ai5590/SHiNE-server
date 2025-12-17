package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain_new.BchBlockEntry_new;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.entities.BlockchainStateEntry;
import utils.files.FileStoreUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class BlockchainStateService_new {

    public static final class Result {
        public final int httpStatus;
        public final String reasonCode; // null если ok
        public final BlockchainStateEntry stateAfter;
        public final int lineIndex;

        public Result(int httpStatus, String reasonCode, BlockchainStateEntry stateAfter, int lineIndex) {
            this.httpStatus = httpStatus;
            this.reasonCode = reasonCode;
            this.stateAfter = stateAfter;
            this.lineIndex = lineIndex;
        }

        public boolean isOk() { return reasonCode == null && httpStatus == 200; }
    }

    private static final BlockchainStateService_new INSTANCE = new BlockchainStateService_new();
    public static BlockchainStateService_new getInstance() { return INSTANCE; }
    private BlockchainStateService_new() {}

    // --- MVP: локи в памяти по blockchainId ---
    private static final ConcurrentHashMap<Long, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private static ReentrantLock lockFor(long blockchainId) {
        return LOCKS.computeIfAbsent(blockchainId, id -> new ReentrantLock());
    }

    public Result addBlockAtomically(
            String login,
            long blockchainId,
            int globalNumber,
            String prevGlobalHashHex,
            String blockBase64
    ) throws SQLException {

        if (login == null || login.isBlank())
            return new Result(400, "EMPTY_LOGIN", null, -1);
        if (blockchainId <= 0)
            return new Result(400, "BAD_BLOCKCHAIN_ID", null, -1);
        if (globalNumber < 0)
            return new Result(400, "BAD_GLOBAL_NUMBER", null, -1);
        if (blockBase64 == null || blockBase64.isBlank())
            return new Result(400, "EMPTY_BLOCK", null, -1);

        byte[] fullBytes;
        try {
            fullBytes = Base64.getDecoder().decode(blockBase64);
        } catch (IllegalArgumentException e) {
            return new Result(400, "BAD_BASE64_BLOCK", null, -1);
        }

        BchBlockEntry_new block;
        try {
            block = new BchBlockEntry_new(fullBytes);
        } catch (Exception e) {
            return new Result(400, "BAD_BLOCK_FORMAT", null, -1);
        }

        int lineIndex = block.line; // short -> int
        if (lineIndex < 0 || lineIndex > 7)
            return new Result(400, "BAD_LINE_INDEX", null, lineIndex);

        ReentrantLock lock = lockFor(blockchainId);
        lock.lock();
        try (Connection conn = SqliteDbController.getInstance().getConnection()) {

            // Транзакция — норм, но БЕЗ "BEGIN IMMEDIATE".
            boolean oldAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                BlockchainStateEntry state =
                        BlockchainStateDAO.getInstance().getByBlockchainId(conn, blockchainId);

                if (state == null) {
                    conn.rollback();
                    return new Result(404, "UNKNOWN_BLOCKCHAIN", null, lineIndex);
                }

                if (!login.equals(state.getUserLogin())) {
                    conn.rollback();
                    return new Result(403, "LOGIN_MISMATCH", state, lineIndex);
                }

                int expectedGlobal = state.getLastGlobalNumber() + 1;
                if (globalNumber != expectedGlobal) {
                    conn.rollback();
                    return new Result(409, "OUT_OF_SEQUENCE_GLOBAL", state, lineIndex);
                }

                String dbPrevGlobalHash = nn(state.getLastGlobalHash());
                if (!eqHash(prevGlobalHashHex, dbPrevGlobalHash)) {
                    conn.rollback();
                    return new Result(409, "GLOBAL_HASH_MISMATCH", state, lineIndex);
                }

                int expectedLineNumber = state.getLastLineNumber(lineIndex) + 1;
                if (block.lineNumber != expectedLineNumber) {
                    conn.rollback();
                    return new Result(409, "OUT_OF_SEQUENCE_LINE", state, lineIndex);
                }

                // prevLineHash (пока просто читаем, дальше пригодится для крипто-проверки)
                String dbPrevLineHashHex = nn(state.getLastLineHash(lineIndex));

                // TODO crypto check (потом подключим)

                // 1) пишем в файл
                FileStoreUtil.getInstance().addDataToBlockchain(blockchainId, block.toBytes());

                // 2) обновляем state в БД
                state.setLastGlobalNumber(globalNumber);
                state.setLastGlobalHash(bytesToHex(block.getHash32()));

                state.setLastLineNumber(lineIndex, block.lineNumber);
                state.setLastLineHash(lineIndex, bytesToHex(block.getHash32()));

                state.setSizeBytes(state.getSizeBytes() + fullBytes.length);
                state.setUpdatedAtMs(System.currentTimeMillis());

                BlockchainStateDAO.getInstance().upsert(conn, state);

                conn.commit();
                return new Result(200, null, state, lineIndex);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(oldAuto);
            }

        } finally {
            lock.unlock();
        }
    }

    private static String nn(String s) { return s == null ? "" : s; }

    private static boolean eqHash(String a, String b) {
        String x = nn(a).trim();
        String y = nn(b).trim();
        return x.equalsIgnoreCase(y);
    }

    private static String bytesToHex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) sb.append(String.format("%02x", v));
        return sb.toString();
    }
}