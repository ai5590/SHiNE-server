package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain.BchBlockEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.entities.BlockEntry;
import shine.db.entities.BlockchainStateEntry;
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
            BchBlockEntry block,
            BlockchainStateEntry stOrNull,
            String newHashHex
    ) throws SQLException {

        // =====================================================================
        // ШАГ 0. КРИТИЧЕСКАЯ ПРОВЕРКА КОНСИСТЕНТНОСТИ:
        //   - если state есть и ожидает ненулевой размер,
        //     то основной файл должен существовать и иметь точно этот размер.
        //   - если не так — это почти наверняка внешнее вмешательство/порча,
        //     и продолжать запись НЕЛЬЗЯ.
        // =====================================================================
        verifyMainFileSizeMatchesStateOrAlert(login, blockchainName, block, stOrNull);

        // =====================================================================
        // ШАГ 1. Готовим bytes нового блока (включая signature+hash)
        // =====================================================================
        final byte[] newBlockFullBytes = block.toBytes(); // ✅ включает хвост signature+hash

        // =====================================================================
        // ШАГ 2. Считаем новый fileSizeBytes
        //   - если genesis (state == null): старый размер = 0
        //   - иначе берём st.fileSizeBytes
        // =====================================================================
        final long oldFileSize = (stOrNull == null) ? 0L : stOrNull.getFileSizeBytes();
        final long newFileSize = safeAdd(oldFileSize, newBlockFullBytes.length);

        // =====================================================================
        // ШАГ 3. Создаём новый tmp-файл:
        //   tmp = (old file bytes) + (new block bytes)
        //
        // Важно:
        //   - читаем старый файл ТОЛЬКО если state не null и size > 0
        //   - если genesis: старого файла нет => tmp = newBlock
        // =====================================================================
        final byte[] tmpBytes;
        if (stOrNull == null || oldFileSize == 0) {
            // genesis: tmp = только новый блок
            tmpBytes = newBlockFullBytes;
        } else {
            // не genesis: tmp = старый файл + новый блок
            byte[] oldBytes;
            try {
                oldBytes = fs.readBlockchain(blockchainName);
            } catch (Exception e) {
                log.error("Ошибка чтения старого файла блокчейна перед записью tmp (login={}, blockchainName={}, oldFileSize={}, blockNumber={})",
                        login, blockchainName, oldFileSize, block.recordNumber, e);
                throw new SQLException("Cannot read old blockchain file for: " + blockchainName, e);
            }

            // (в идеале это всегда должно совпадать после verifyMainFileSizeMatchesStateOrAlert)
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

        // Пишем tmp на диск ДО транзакции БД:
        // - если сервер упадёт позже — tmp останется, но БД может не успеть обновиться (это ок для recovery)
        try {
            fs.writeBlockchainTmp(blockchainName, tmpBytes);
        } catch (Exception e) {
            log.error("Ошибка записи tmp файла блокчейна (login={}, blockchainName={}, tmpBytesLen={}, oldFileSize={}, newFileSize={}, blockNumber={})",
                    login, blockchainName, tmpBytes.length, oldFileSize, newFileSize, block.recordNumber, e);
            throw new SQLException("Cannot write tmp blockchain file for: " + blockchainName, e);
        }

        // =====================================================================
        // ШАГ 4. АТОМАРНО фиксируем БД:
        //   - UPSERT blocks
        //   - UPSERT blockchain_state (включая fileSizeBytes = newFileSize)
        // =====================================================================
        try (Connection c = db.getConnection()) {

            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);

            boolean committed = false;

            try {
                // 4.1) вставляем/апдейтим запись блока
                insertBlockRow(c, login, blockchainName, prevGlobalHashHex, block);

                // 4.2) апдейтим состояние (включая fileSizeBytes)
                appendState(c, blockchainName, block.recordNumber, stOrNull, newHashHex, newFileSize);

                // 4.3) commit
                c.commit();
                committed = true;

            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException ignore) {}

                log.error("Ошибка транзакции БД при добавлении блока (rollback выполнен) (login={}, blockchainName={}, blockNumber={}, prevHash={}, newHash={}, oldFileSize={}, newFileSize={})",
                        login, blockchainName, block.recordNumber, prevGlobalHashHex, newHashHex, oldFileSize, newFileSize, e);

                if (e instanceof SQLException se) throw se;
                throw new SQLException("appendBlockAndState failed (db tx)", e);

            } finally {
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) {}
            }

            // =================================================================
            // ШАГ 5. После успешного коммита БД — атомарно заменяем файл:
            //    <name>.tmp_bch -> <name>.bch
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

    /**
     * Проверка: реальный размер <name>.bch должен совпадать с st.fileSizeBytes.
     * Если нет — это критическая внешняя порча/вмешательство, уведомляем админа и падаем.
     */
    private void verifyMainFileSizeMatchesStateOrAlert(
            String login,
            String blockchainName,
            BchBlockEntry block,
            BlockchainStateEntry stOrNull
    ) throws SQLException {

        if (stOrNull == null) {
            // genesis — state ещё нет, проверять нечего
            return;
        }

        long expected = stOrNull.getFileSizeBytes();
        if (expected <= 0) {
            // state есть, но ожидаемый размер 0 — это либо пустая цепочка, либо старый формат.
            // Здесь не трогаем (но можно усилить правила позже).
            return;
        }

        String mainFileName = fs.buildBlockchainFileName(blockchainName);

        // Если файла нет — это уже очень подозрительно: state говорит “файл есть и размер > 0”
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
     * Пока линии не используются: lineIndex=0 и lineHash = globalHash.
     *
     * + обновляем fileSizeBytes
     */
    private void appendState(
            Connection c,
            String blockchainName,
            int globalNumber,
            BlockchainStateEntry stOrNull,
            String newHashHex,
            long newFileSizeBytes
    ) throws SQLException {

        BlockchainStateEntry st = stOrNull;
        if (st == null) {
            st = new BlockchainStateEntry();
            st.setBlockchainName(blockchainName);
        }

        // Последний глобальный блок
        st.setLastGlobalNumber(globalNumber);
        st.setLastGlobalHash(newHashHex);

        // Линии пока не используются
        st.setLastLineNumber(0, globalNumber);
        st.setLastLineHash(0, newHashHex);

        // ✅ ВАЖНО: сохраняем ожидаемый размер файла
        st.setFileSizeBytes(newFileSizeBytes);

        // Метка времени обновления
        st.setUpdatedAtMs(System.currentTimeMillis());

        // UPSERT
        stateDAO.upsert(c, st);
    }

    /**
     * Вставка/апдейт строки блока в blocks.
     */
    private void insertBlockRow(
            Connection c,
            String login,
            String blockchainName,
            String prevGlobalHashHex,
            BchBlockEntry block
    ) throws SQLException {

        BlockEntry e = new BlockEntry();

        e.setLogin(login);
        e.setBchName(blockchainName);

        e.setBlockGlobalNumber(block.recordNumber);
        e.setBlockGlobalPreHashe(prevGlobalHashHex);

        // линии пока не используем
        e.setBlockLineIndex(0);
        e.setBlockLineNumber(block.recordNumber);
        e.setBlockLinePreHashe(prevGlobalHashHex);

        // тип сообщения — по body.type()
        e.setMsgType(block.body.type());

        // полный блок (RAW + signature + hash)
        e.setBlockByte(block.toBytes());

        e.setToLogin(null);
        e.setToBchName(null);
        e.setToBlockGlobalNumber(null);
        e.setToBlockHashe(null);

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