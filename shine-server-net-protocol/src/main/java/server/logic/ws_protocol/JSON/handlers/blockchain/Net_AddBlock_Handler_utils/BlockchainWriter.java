package server.logic.ws_protocol.JSON.handlers.blockchain.Net_AddBlock_Handler_utils;

import blockchain.BchBlockEntry;
import blockchain.body.ReactionBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.entities.BlockEntry;
import shine.db.entities.BlockchainStateEntry;
import utils.blockchain.BlockchainNameUtil;
import utils.files.FileStoreUtil;
import shine.log.BlockchainAdminNotifier;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * BlockchainWriter — единая точка записи:
 *   1) создаём новый файл <name>.tmp_bch = oldFileBytes + newBlockBytes
 *   2) атомарно фиксируем в БД:
 *        - blocks (строка блока)
 *        - blockchain_state (включая новый fileSizeBytes)
 *   3) атомарно заменяем файл:
 *        - удаляем/замещаем старый <name>.bch
 *        - переименовываем <name>.tmp_bch -> <name>.bch
 *
 * Важно:
 *  - Шаг (2) — строго атомарный (SQL tx).
 *  - Шаг (3) — атомарный на уровне ФС, если поддерживается ATOMIC_MOVE.
 *
 * ДОПОЛНЕНИЕ (КРИТИЧНО):
 *  - Перед тем как дописывать блок, проверяем:
 *      реальный размер <name>.bch == st.fileSizeBytes.
 *    Если не совпадает — считаем это критической внешней порчей файлов,
 *    шлём уведомление админу и НЕ продолжаем запись.
 */
public final class BlockchainWriter {

    private static final Logger log = LoggerFactory.getLogger(BlockchainWriter.class);

    private static final String ZERO_HASH_64 =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final SqliteDbController db;
    private final BlocksDAO blocksDAO;
    private final BlockchainStateDAO stateDAO;
    private final FileStoreUtil fs;

    public BlockchainWriter(BlocksDAO blocksDAO, BlockchainStateDAO stateDAO) {
        this.db = SqliteDbController.getInstance();
        this.blocksDAO = blocksDAO;
        this.stateDAO = stateDAO;
        this.fs = FileStoreUtil.getInstance();
    }

    /**
     * Главный метод:
     *  - (0) проверяет соответствие размера файла и state (если это не genesis)
     *  - создаёт tmp-файл (старое+новое),
     *  - атомарно коммитит БД (block+state),
     *  - атомарно заменяет основной файл.
     */
    public void appendBlockAndState(
            String login,
            String blockchainName,
            String prevGlobalHashHex,
            String prevLineHashHex,
            BchBlockEntry block,
            BlockchainStateEntry stOrNull,
            String newHashHex
    ) throws SQLException {

        // ✅ ВАЖНО: state теперь ОБЯЗАТЕЛЕН, genesis НЕ создаёт запись, а обновляет существующую
        if (stOrNull == null) {
            throw new SQLException("blockchain_state not found for blockchainName=" + blockchainName + " (state обязателен)");
        }

        verifyMainFileSizeMatchesStateOrAlert(login, blockchainName, block, stOrNull);

        // =====================================================================
        // ШАГ 1. Готовим bytes нового блока (включая signature+hash)
        // =====================================================================
        final byte[] newBlockFullBytes = block.toBytes();

        // =====================================================================
        // ШАГ 2. Считаем новый fileSizeBytes
        // =====================================================================
        final long oldFileSize = stOrNull.getFileSizeBytes();
        final long newFileSize = safeAdd(oldFileSize, newBlockFullBytes.length);

        // =====================================================================
        // ШАГ 3. Создаём новый tmp-файл: tmp = (old file bytes) + (new block bytes)
        // =====================================================================
        final byte[] tmpBytes;
        if (oldFileSize == 0) {
            tmpBytes = newBlockFullBytes;
        } else {
            byte[] oldBytes;
            try {
                oldBytes = fs.readBlockchain(blockchainName);
            } catch (Exception e) {
                log.error("Ошибка чтения старого файла блокчейна перед записью tmp (login={}, blockchainName={}, oldFileSize={}, blockNumber={})",
                        login, blockchainName, oldFileSize, block.recordNumber, e);
                throw new SQLException("Cannot read old blockchain file for: " + blockchainName, e);
            }

            if (oldBytes.length != (int) oldFileSize) {
                String msg =
                        "Несовпадение размера файла блокчейна при чтении: " +
                        "state ожидал oldFileSize=" + oldFileSize +
                        ", а реально прочитали oldBytes.length=" + oldBytes.length +
                        " (login=" + login +
                        ", blockchainName=" + blockchainName +
                        ", blockNumber=" + block.recordNumber + ").";
                BlockchainAdminNotifier.critical(msg, null);
                throw new SQLException(msg);
            }

            tmpBytes = concat(oldBytes, newBlockFullBytes);
        }

        try {
            fs.writeBlockchainTmp(blockchainName, tmpBytes);
        } catch (Exception e) {
            log.error("Ошибка записи tmp файла блокчейна (login={}, blockchainName={}, tmpBytesLen={}, oldFileSize={}, newFileSize={}, blockNumber={})",
                    login, blockchainName, tmpBytes.length, oldFileSize, newFileSize, block.recordNumber, e);
            throw new SQLException("Cannot write tmp blockchain file for: " + blockchainName, e);
        }

        // =====================================================================
        // ШАГ 4. АТОМАРНО фиксируем БД
        // =====================================================================
        try (Connection c = db.getConnection()) {

            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);

            boolean committed = false;

            try {
                insertBlockRow(c, login, blockchainName, prevGlobalHashHex, prevLineHashHex, block);

                appendState(c, blockchainName, block, stOrNull, newHashHex, newFileSize);

                c.commit();
                committed = true;

            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException ignore) {}

                log.error("Ошибка транзакции БД при добавлении блока (rollback выполнен) (login={}, blockchainName={}, blockNumber={}, prevGlobalHash={}, prevLineHash={}, newHash={}, oldFileSize={}, newFileSize={})",
                        login, blockchainName, block.recordNumber, prevGlobalHashHex, prevLineHashHex, newHashHex, oldFileSize, newFileSize, e);

                if (e instanceof SQLException se) throw se;
                throw new SQLException("appendBlockAndState failed (db tx)", e);

            } finally {
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) {}
            }

            // =================================================================
            // ШАГ 5. После успешного коммита БД — атомарно заменяем файл
            // =================================================================
            if (committed) {
                try {
                    fs.atomicReplaceBlockchainFile(blockchainName);
                } catch (Exception moveError) {
                    log.error("БД закоммичена, но атомарная замена файла блокчейна не удалась. tmp оставлен для recovery. (login={}, blockchainName={}, blockNumber={}, newHash={}, tmpBytesLen={})",
                            login, blockchainName, block.recordNumber, newHashHex, tmpBytes.length, moveError);

                    throw new SQLException(
                            "DB committed but file replace failed; tmp kept for recovery. blockchainName=" + blockchainName,
                            moveError
                    );
                }
            }
        }
    }

    private void verifyMainFileSizeMatchesStateOrAlert(
            String login,
            String blockchainName,
            BchBlockEntry block,
            BlockchainStateEntry stOrNull
    ) throws SQLException {

        if (stOrNull == null) return;

        long expected = stOrNull.getFileSizeBytes();
        if (expected <= 0) return;

        String mainFileName = fs.buildBlockchainFileName(blockchainName);

        if (!fs.exists(mainFileName)) {
            String msg =
                    "КРИТИЧЕСКАЯ ОШИБКА КОНСИСТЕНТНОСТИ: state ожидает основной файл, но его нет. " +
                    "login=" + login +
                    ", blockchainName=" + blockchainName +
                    ", expectedSizeFromState=" + expected +
                    ", blockNumber=" + (block != null ? block.recordNumber : -1) + ".";

            BlockchainAdminNotifier.critical(msg, null);
            throw new SQLException(msg);
        }

        long real;
        try {
            real = fs.size(mainFileName);
        } catch (Exception e) {
            String msg =
                    "КРИТИЧЕСКАЯ ОШИБКА: не удалось получить размер основного файла блокчейна. " +
                    "login=" + login +
                    ", blockchainName=" + blockchainName +
                    ", expectedSizeFromState=" + expected +
                    ", blockNumber=" + (block != null ? block.recordNumber : -1) + ".";
            BlockchainAdminNotifier.critical(msg, e);
            throw new SQLException(msg, e);
        }

        if (real != expected) {
            String msg =
                    "КРИТИЧЕСКАЯ ОШИБКА КОНСИСТЕНТНОСТИ: размер файла блокчейна НЕ СОВПАДАЕТ с state. " +
                    "login=" + login +
                    ", blockchainName=" + blockchainName +
                    ", expectedSizeFromState=" + expected +
                    ", realMainFileSize=" + real +
                    ", blockNumber=" + (block != null ? block.recordNumber : -1) + ". " +
                    "Похоже на внешнее вмешательство/порчу файла. Запись нового блока остановлена.";

            BlockchainAdminNotifier.critical(msg, null);
            throw new SQLException(msg);
        }
    }

    /**
     * Обновление состояния blockchain_state (создаём если отсутствует).
     *
     * ПРАВИЛО ЛИНИЙ (как ты описал):
     *  - globalNumber=0 — genesis в lineIndex=0, lineNumber=0, и его hash — базовый для ВСЕХ линий.
     *  - для lineIndex>0 первая запись имеет lineNumber=1, её prevLineHash = hash(genesis)
     *  - lastLineNumber/lastLineHash ведём независимо по каждой линии.
     */
    private void appendState(
            Connection c,
            String blockchainName,
            BchBlockEntry block,
            BlockchainStateEntry stOrNull,
            String newHashHex,
            long newFileSizeBytes
    ) throws SQLException {

        // ✅ state обязателен
        BlockchainStateEntry st = stOrNull;
        if (st == null) {
            throw new SQLException("blockchain_state not found for blockchainName=" + blockchainName);
        }

        // глобальная цепочка всегда растёт по recordNumber
        st.setLastGlobalNumber(block.recordNumber);
        st.setLastGlobalHash(newHashHex);

        // обновляем конкретную линию блока
        int li = block.lineIndex;
        st.setLastLineNumber(li, block.lineNumber);
        st.setLastLineHash(li, newHashHex);

        // file size
        st.setFileSizeBytes(newFileSizeBytes);

        // timestamp
        st.setUpdatedAtMs(System.currentTimeMillis());

        stateDAO.upsert(c, st);
    }

    /**
     * Вставка/апдейт строки блока в blocks.
     *
     * Важно:
     *  - blockLinePreHashe = prevLineHashHex (а НЕ prevGlobalHashHex)
     *  - msgType = body.type()
     *  - Для ReactionBody заполняем toBchName/toBlockGlobalNumber/toBlockHashe (+ to_login если можем).
     */
    private void insertBlockRow(
            Connection c,
            String login,
            String blockchainName,
            String prevGlobalHashHex,
            String prevLineHashHex,
            BchBlockEntry block
    ) throws SQLException {

        BlockEntry e = new BlockEntry();

        e.setLogin(login);
        e.setBchName(blockchainName);

        e.setBlockGlobalNumber(block.recordNumber);
        e.setBlockGlobalPreHashe(prevGlobalHashHex);

        e.setBlockLineIndex(block.lineIndex);
        e.setBlockLineNumber(block.lineNumber);

        // ✅ минимальная правка: для genesis сохраняем именно "64 нуля", а не пустую строку/NULL
        String linePre = prevLineHashHex;
        if (block.recordNumber == 0 && (linePre == null || linePre.isBlank())) {
            linePre = ZERO_HASH_64;
        }
        e.setBlockLinePreHashe(linePre);

        e.setMsgType(block.body.type());

        e.setBlockByte(block.toBytes());

        // defaults
        e.setToLogin(null);
        e.setToBchName(null);
        e.setToBlockGlobalNumber(null);
        e.setToBlockHashe(null);

        // ReactionBody -> target fields
        if (block.body instanceof ReactionBody rb) {
            e.setToBchName(rb.toBlockchainName);
            e.setToBlockGlobalNumber(rb.toBlockGlobalNumber);
            e.setToBlockHashe(rb.toBlockHashHex());

            // optional: try compute to_login from target chain name (для индекса idx_blocks_to_target)
            String toLogin = BlockchainNameUtil.loginFromBlockchainName(rb.toBlockchainName);
            if (toLogin != null && !toLogin.isBlank()) {
                e.setToLogin(toLogin);
            }
        }

        blocksDAO.upsert(c, e);
    }

    /* ===================================================================== */
    /* =============================== Utils ================================ */
    /* ===================================================================== */

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static long safeAdd(long x, long y) {
        long r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new IllegalArgumentException("fileSizeBytes overflow: " + x + " + " + y);
        }
        return r;
    }
}