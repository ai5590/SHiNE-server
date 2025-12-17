package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain_new.BchBlockEntry_new;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;
import utils.files.FileStoreUtil;

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

    // ===== locks per blockchainId (MVP: один сервер) =====
    private static final ConcurrentHashMap<Long, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private static ReentrantLock lockFor(long blockchainId) {
        return LOCKS.computeIfAbsent(blockchainId, id -> new ReentrantLock());
    }

    // ===== constants =====
    private static final String ZERO64 = "0".repeat(64);

    // MVP: “заглавный блок”
    // (пока без парсинга тела, просто по номеру)
    private static boolean isHeaderBlock(int globalNumber, int lineNumber) {
        return globalNumber == 0 && lineNumber == 0;
    }

    public Result addBlock(
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
        try {
            BlockchainStateEntry state = BlockchainStateDAO.getInstance().getByBlockchainId(blockchainId);

            // ===== GENESIS ветка: state ещё нет =====
            if (state == null) {
                // разрешаем только заглавный блок
                if (!isHeaderBlock(globalNumber, block.lineNumber)) {
                    return new Result(404, "UNKNOWN_BLOCKCHAIN", null, lineIndex);
                }

                // создаём первичное состояние (last_global=-1, hash=ZERO64, lines=0/ZERO64)
                state = createInitialStateFromUser(login, blockchainId);
                if (state == null) {
                    // нет такого юзера / не его bchId
                    return new Result(404, "UNKNOWN_BLOCKCHAIN", null, lineIndex);
                }

                // сохраняем стартовую строку
                BlockchainStateDAO.getInstance().upsert(state);
            }

            // 1) защита от подмены логина
            if (!login.equals(state.getUserLogin())) {
                return new Result(403, "LOGIN_MISMATCH", state, lineIndex);
            }

            // 2) expected global: last_global + 1 (у нас last_global стартует -1)
            int expectedGlobal = state.getLastGlobalNumber() + 1;
            if (globalNumber != expectedGlobal) {
                return new Result(409, "OUT_OF_SEQUENCE_GLOBAL", state, lineIndex);
            }

            // 3) prev global hash
            String dbPrevGlobalHash = nn(state.getLastGlobalHash());
            if (!eqHash(prevGlobalHashHex, dbPrevGlobalHash)) {
                return new Result(409, "GLOBAL_HASH_MISMATCH", state, lineIndex);
            }

            // 4) lineNumber
            // Нормально: первый “обычный” блок по линии должен быть lineNumber=1 при lastLine=0
            // Исключение: заглавный блок имеет lineNumber=0
            int expectedLineNumber = state.getLastLineNumber(lineIndex) + 1;
            boolean header = isHeaderBlock(globalNumber, block.lineNumber);

            if (!header) {
                if (block.lineNumber != expectedLineNumber) {
                    return new Result(409, "OUT_OF_SEQUENCE_LINE", state, lineIndex);
                }
            } else {
                // заглавный блок допускаем только если текущий lastLineNumber == 0 и пришёл 0
                if (state.getLastLineNumber(lineIndex) != 0 || block.lineNumber != 0) {
                    return new Result(409, "BAD_HEADER_LINE_NUMBER", state, lineIndex);
                }
            }

            // 5) prevLineHash берём из БД (пока просто читаем)
            String dbPrevLineHashHex = nn(state.getLastLineHash(lineIndex));
            // (можешь позже сравнивать с тем, что внутри блока, если там есть prevLineHash)

            // 6) крипто-проверка (позже)
            // TODO:
            // - восстановить preimage
            // - sha256(preimage) == block.hash32
            // - Ed25519 verify signature
            // если не ок: return new Result(422, "CRYPTO_INVALID", state, lineIndex);

            // 7) запись блока в файл
            FileStoreUtil.getInstance().addDataToBlockchain(blockchainId, block.toBytes());

            // 8) апдейт состояния
            state.setLastGlobalNumber(globalNumber);
            state.setLastGlobalHash(bytesToHex(block.getHash32()));

            // line number:
            // - для заглавного блока оставляем 0
            // - для остальных двигаем как обычно
            if (!header) {
                state.setLastLineNumber(lineIndex, block.lineNumber);
            } else {
                state.setLastLineNumber(lineIndex, 0);
            }

            // line hash обновляем в любом случае (так проще для цепочки)
            state.setLastLineHash(lineIndex, bytesToHex(block.getHash32()));

            state.setSizeBytes(state.getSizeBytes() + fullBytes.length);
            state.setUpdatedAtMs(System.currentTimeMillis());

            BlockchainStateDAO.getInstance().upsert(state);

            return new Result(200, null, state, lineIndex);

        } catch (SQLException e) {
            throw e;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Создаёт стартовое состояние по данным пользователя:
     * - проверяем, что login существует и что bchId совпадает с blockchainId
     * - public_key_base64 берём из loginKey
     */
    private static BlockchainStateEntry createInitialStateFromUser(String login, long blockchainId) throws SQLException {
        SolanaUserEntry u = SolanaUsersDAO.getInstance().getByLogin(login);
        if (u == null) return null;
        if (u.getBchId() != blockchainId) return null;

        BlockchainStateEntry s = new BlockchainStateEntry();
        s.setBlockchainId(blockchainId);
        s.setUserLogin(login);

        // публичный ключ для блокчейна = loginKey (как ты и хочешь)
        s.setPublicKeyBase64(nn(u.getLoginKey()));

        // лимит (пока тестовый / из пользователя)
        int limit = (u.getBchLimit() != null) ? u.getBchLimit() : 1_000_000;
        s.setSizeLimit(limit);

        s.setSizeBytes(0);

        // ВАЖНО: стартовые значения
        s.setLastGlobalNumber(-1);
        s.setLastGlobalHash(ZERO64);

        for (int i = 0; i < 8; i++) {
            s.setLastLineNumber(i, 0);
            s.setLastLineHash(i, ZERO64);
        }

        s.setUpdatedAtMs(System.currentTimeMillis());
        return s;
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