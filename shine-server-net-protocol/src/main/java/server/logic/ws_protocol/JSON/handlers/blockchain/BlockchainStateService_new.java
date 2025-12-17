package server.logic.blockchain_new;

import blockchain_new.BchBlockEntry_new;
import blockchain_new.BchCryptoVerifier_new;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.entities.BlockchainStateEntry;
import utils.files.FileStoreUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockchainStateService_new {

    private static final BlockchainStateService_new INSTANCE = new BlockchainStateService_new();

    public static BlockchainStateService_new getInstance() { return INSTANCE; }

    private final SqliteDbController db = SqliteDbController.getInstance();
    private final BlockchainStateDAO stateDao = BlockchainStateDAO.getInstance();
    private final FileStoreUtil fileStore = FileStoreUtil.getInstance();

    /** JVM-level locks per blockchainId */
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    private BlockchainStateService_new() {}

    public static final class ApplyResult {
        public final int newGlobalNumber;
        public final String newGlobalHashHex;
        public final int newLineNumber;
        public final String newLineHashHex;
        public final int sizeBytes;

        public ApplyResult(int newGlobalNumber, String newGlobalHashHex,
                           int newLineNumber, String newLineHashHex,
                           int sizeBytes) {
            this.newGlobalNumber = newGlobalNumber;
            this.newGlobalHashHex = newGlobalHashHex;
            this.newLineNumber = newLineNumber;
            this.newLineHashHex = newLineHashHex;
            this.sizeBytes = sizeBytes;
        }
    }

    public ApplyResult applyAddBlock(
            String userLogin,
            long blockchainId,
            int globalBlockNumber,
            String prevGlobalHashHexFromClient,
            short lineIndex,
            int lineBlockNumber,
            String blockBase64
    ) throws Exception {

        Objects.requireNonNull(userLogin, "userLogin == null");
        Objects.requireNonNull(blockBase64, "blockBase64 == null");

        if (blockchainId <= 0) throw new IllegalArgumentException("blockchainId <= 0");
        if (globalBlockNumber < 0) throw new IllegalArgumentException("globalBlockNumber < 0");
        if (lineIndex < 0 || lineIndex > 7) throw new IllegalArgumentException("lineIndex must be 0..7");
        if (lineBlockNumber < 0) throw new IllegalArgumentException("lineBlockNumber < 0");

        byte[] fullBytes;
        try {
            fullBytes = Base64.getDecoder().decode(blockBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("blockBase64 is not valid Base64", e);
        }

        BchBlockEntry_new block = new BchBlockEntry_new(fullBytes);

        // Быстрая проверка: что клиентские “в шапке запроса” совпадают с тем, что внутри блока.
        if (block.recordNumber != globalBlockNumber)
            throw new IllegalArgumentException("Global number mismatch: req=" + globalBlockNumber + " block=" + block.recordNumber);
        if (block.line != lineIndex)
            throw new IllegalArgumentException("Line mismatch: req=" + lineIndex + " block=" + block.line);
        if (block.lineNumber != lineBlockNumber)
            throw new IllegalArgumentException("LineBlockNumber mismatch: req=" + lineBlockNumber + " block=" + block.lineNumber);

        Object lock = locks.computeIfAbsent(blockchainId, k -> new Object());

        synchronized (lock) {
            Connection conn = db.getConnection();
            boolean prevAutoCommit = conn.getAutoCommit();

            try {
                conn.setAutoCommit(false);

                // SQLite writer-lock
                try (Statement st = conn.createStatement()) {
                    st.execute("BEGIN IMMEDIATE");
                }

                BlockchainStateEntry state = stateDao.getByBlockchainId(blockchainId);
                if (state == null)
                    throw new IllegalStateException("BLOCKCHAIN_NOT_FOUND: id=" + blockchainId);

                // 1) логин должен совпадать с тем, что хранится в state (иначе легко подделывать)
                if (!userLogin.equals(state.getUserLogin()))
                    throw new IllegalStateException("LOGIN_MISMATCH: requestLogin=" + userLogin + " dbLogin=" + state.getUserLogin());

                // 2) глобальная последовательность
                int expectedGlobal = state.getLastGlobalNumber() + 1;
                if (globalBlockNumber != expectedGlobal)
                    throw new IllegalStateException("BAD_GLOBAL_NUMBER: expected=" + expectedGlobal + " got=" + globalBlockNumber);

                String prevGlobalHashHexDb = nn(state.getLastGlobalHash());
                String prevGlobalHashHexClient = nn(prevGlobalHashHexFromClient);

                // 3) prev global hash должен совпасть с db
                if (!eqHash(prevGlobalHashHexDb, prevGlobalHashHexClient))
                    throw new IllegalStateException("BAD_PREV_GLOBAL_HASH");

                // 4) line последовательность
                int expectedLine = state.getLastLineNumber(lineIndex) + 1;
                if (lineBlockNumber != expectedLine)
                    throw new IllegalStateException("BAD_LINE_NUMBER: expected=" + expectedLine + " got=" + lineBlockNumber);

                String prevLineHashHexDb = nn(state.getLastLineHash(lineIndex));

                // 5) криптография: проверка хэша и подписи
                byte[] publicKey32 = decodeBase64_32(state.getPublicKeyBase64());
                if (publicKey32 == null)
                    throw new IllegalStateException("BAD_PUBLIC_KEY_BASE64 in db");

                byte[] prevGlobalHash32 = hexTo32(prevGlobalHashHexDb);
                byte[] prevLineHash32   = hexTo32(prevLineHashHexDb);

                byte[] rawBytes = block.getRawBytes(); // нужно добавить метод в BchBlockEntry_new
                byte[] preimage = BchCryptoVerifier_new.buildPreimage(
                        userLogin,
                        prevGlobalHash32,
                        prevLineHash32,
                        rawBytes
                );

                byte[] expectedHash32 = BchCryptoVerifier_new.sha256(preimage);

                if (!constTimeEq32(expectedHash32, block.getHash32()))
                    throw new IllegalStateException("HASH_MISMATCH");

                // Подпись — тут подключишь свой Ed25519 util (сейчас у тебя в new-верификаторе TODO)
                boolean sigOk = BchCryptoVerifier_new.verifySignature(
                        expectedHash32,
                        block.getSignature64(),
                        publicKey32
                );
                if (!sigOk)
                    throw new IllegalStateException("SIGNATURE_MISMATCH");

                // 6) лимит / размер
                int newSizeBytes = state.getSizeBytes() + block.recordSize;
                if (newSizeBytes > state.getSizeLimit())
                    throw new IllegalStateException("SIZE_LIMIT_EXCEEDED");

                // 7) Сначала дописываем файл (если упадёт — транзакция откатится)
                fileStore.addDataToBlockchain(blockchainId, block.toBytes());

                // 8) Апдейт state в памяти
                state.setSizeBytes(newSizeBytes);
                state.setLastGlobalNumber(globalBlockNumber);
                String newGlobalHashHex = toHex(expectedHash32);
                state.setLastGlobalHash(newGlobalHashHex);

                state.setLastLineNumber(lineIndex, lineBlockNumber);
                String newLineHashHex = newGlobalHashHex; // если глобальный hash = hash блока (обычно да)
                state.setLastLineHash(lineIndex, newLineHashHex);

                state.setUpdatedAtMs(System.currentTimeMillis());

                // 9) UPSERT в БД
                stateDao.upsert(state);

                // 10) commit
                conn.commit();

                return new ApplyResult(
                        globalBlockNumber,
                        newGlobalHashHex,
                        lineBlockNumber,
                        newLineHashHex,
                        newSizeBytes
                );

            } catch (Exception e) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw e;
            } finally {
                try { conn.setAutoCommit(prevAutoCommit); } catch (SQLException ignore) {}
            }
        }
    }

    // ---------------- helpers ----------------

    private static String nn(String s) { return s == null ? "" : s; }

    /** сравнение хэшей: пустой == "0"*? — упростим: пустой = пустой. */
    private static boolean eqHash(String a, String b) {
        return nn(a).equalsIgnoreCase(nn(b));
    }

    private static byte[] decodeBase64_32(String b64) {
        try {
            byte[] x = Base64.getDecoder().decode(b64);
            return (x != null && x.length == 32) ? x : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] hexTo32(String hex) {
        if (hex == null || hex.isBlank()) return new byte[32];
        String h = hex.trim();
        if (h.length() != 64) throw new IllegalArgumentException("hex must be 64 chars (or empty)");
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(h.charAt(i * 2), 16);
            int lo = Character.digit(h.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static boolean constTimeEq32(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != 32 || b.length != 32) return false;
        int r = 0;
        for (int i = 0; i < 32; i++) r |= (a[i] ^ b[i]);
        return r == 0;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) sb.append(String.format("%02x", v));
        return sb.toString();
    }
}