package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain_new.BchBlockEntry_new;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;
import utils.files.FileStoreUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private static final String ZERO64 = "0".repeat(64);

    // Локи по blockchainId (MVP, один сервер)
    private final ConcurrentMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    private ReentrantLock lockFor(long blockchainId) {
        return locks.computeIfAbsent(blockchainId, k -> new ReentrantLock());
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

        int lineIndex = block.line;
        if (lineIndex < 0 || lineIndex > 7)
            return new Result(400, "BAD_LINE_INDEX", null, lineIndex);

        ReentrantLock lock = lockFor(blockchainId);
        lock.lock();
        try (Connection conn = SqliteDbController.getInstance().getConnection()) {

            BlockchainStateDAO stateDao = BlockchainStateDAO.getInstance();
            SolanaUsersDAO usersDao = SolanaUsersDAO.getInstance();

            // читаем state В ЭТОМ ЖЕ conn
            BlockchainStateEntry state = stateDao.getByBlockchainId(conn, blockchainId);

            boolean isHeaderBlock = (globalNumber == 0 && lineIndex == 0 && block.lineNumber == 0);

            if (state == null) {
                // state отсутствует — разрешаем ТОЛЬКО header-блок
                if (!isHeaderBlock) {
                    return new Result(404, "UNKNOWN_BLOCKCHAIN", null, lineIndex);
                }

                // Проверяем пользователя и соответствие bchId
                SolanaUserEntry u = usersDao.getByLogin(conn, login);
                if (u == null) {
                    return new Result(404, "UNKNOWN_USER", null, lineIndex);
                }
                if (u.getBchId() != blockchainId) {
                    return new Result(403, "BCHID_MISMATCH", null, lineIndex);
                }

                // prevGlobalHash для header должен быть нулевой
                if (!eqHash(prevGlobalHashHex, ZERO64)) {
                    return new Result(409, "GLOBAL_HASH_MISMATCH", null, lineIndex);
                }

                // Создаём “нулевой” state ДО записи header (last_global_number = -1)
                state = createInitialState(blockchainId, login, u.getLoginKey(), safeLimit(u.getBchLimit()));
//                stateDao.upsert(conn, state);  //TODO так здесь наверное его в БД сохранять не надо если всё верно то потом дополненный сохраниться
            } else {
                // state есть — обычная проверка login
                if (!login.equals(state.getUserLogin())) {
                    return new Result(403, "LOGIN_MISMATCH", state, lineIndex);
                }
            }

            // Перечитывать не надо, state актуален в переменной.

            // expected global
            int expectedGlobal = state.getLastGlobalNumber() + 1;
            if (globalNumber != expectedGlobal) {
                return new Result(409, "OUT_OF_SEQUENCE_GLOBAL", state, lineIndex);
            }

            // prev global hash сверяем
            String dbPrevGlobalHash = nn(state.getLastGlobalHash());
            if (!eqHash(prevGlobalHashHex, dbPrevGlobalHash)) {
                return new Result(409, "GLOBAL_HASH_MISMATCH", state, lineIndex);
            }

            // expected line number
            int expectedLineNumber = state.getLastLineNumber(lineIndex) + 1;
            if (block.lineNumber != expectedLineNumber) {
                return new Result(409, "OUT_OF_SEQUENCE_LINE", state, lineIndex);
            }

            // TODO: крипто-проверка (потом подключим)

            // 1) запись блока в файл
            FileStoreUtil.getInstance().addDataToBlockchain(blockchainId, block.toBytes());

            // 2) апдейт state
            String newHashHex = bytesToHex(block.getHash32());

            state.setLastGlobalNumber(globalNumber);
            state.setLastGlobalHash(newHashHex);

            state.setLastLineNumber(lineIndex, block.lineNumber);
            state.setLastLineHash(lineIndex, newHashHex);

            state.setSizeBytes(state.getSizeBytes() + fullBytes.length);
            state.setUpdatedAtMs(System.currentTimeMillis());

            stateDao.upsert(conn, state);

            return new Result(200, null, state, lineIndex);

        } catch (SQLException e) {
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private static BlockchainStateEntry createInitialState(long blockchainId,
                                                           String login,
                                                           String loginKeyBase64,
                                                           int sizeLimit) {
        BlockchainStateEntry s = new BlockchainStateEntry();
        s.setBlockchainId(blockchainId);
        s.setUserLogin(login);
        s.setPublicKeyBase64(nn(loginKeyBase64));

        s.setSizeLimit(sizeLimit);
        s.setSizeBytes(0);

        // как ты хочешь:
        s.setLastGlobalNumber(-1);
        s.setLastGlobalHash(ZERO64);

        for (int i = 0; i < 8; i++) {
            if (i == 0) {
                // линия 0: заглавный блок имеет lineNumber=0
                s.setLastLineNumber(i, -1);
            } else {
                // остальные линии: первый блок будет lineNumber=1
                s.setLastLineNumber(i, 0);
            }
            s.setLastLineHash(i, ZERO64);
        }

        s.setUpdatedAtMs(System.currentTimeMillis());
        return s;
    }

    private static int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) return 1_000_000; // fallback (test)
        return limit;
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