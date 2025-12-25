package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain.BchBlockEntry;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.entities.BlockEntry;
import shine.db.entities.BlockchainStateEntry;
import utils.files.FileStoreUtil;

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
 *  - Если сервер упадёт между (2) и (3), останется tmp — твой recovery при старте починит.
 */
public final class BlockchainWriter {

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
                // Здесь лучше падать: state говорит, что файл есть, а прочитать нельзя.
                throw new SQLException("Cannot read old blockchain file for: " + blockchainName, e);
            }

            // (на будущее) можно проверять согласованность: oldBytes.length == oldFileSize
            // но ты всё равно будешь делать recovery при старте — оставим как подсказку.

            tmpBytes = concat(oldBytes, newBlockFullBytes);
        }

        // Пишем tmp на диск ДО транзакции БД:
        // - если сервер упадёт позже — tmp останется, но БД может не успеть обновиться (это ок для recovery)
        try {
            fs.writeBlockchainTmp(blockchainName, tmpBytes);
        } catch (Exception e) {
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

                if (e instanceof SQLException se) throw se;
                throw new SQLException("appendBlockAndState failed (db tx)", e);

            } finally {
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) {}
            }

            // =================================================================
            // ШАГ 5. После успешного коммита БД — атомарно заменяем файл:
            //    <name>.tmp_bch -> <name>.bch
            //
            // Если тут упадём:
            //   - БД уже обновлена
            //   - tmp остаётся
            //   - recovery при старте восстановит консистентность
            // =================================================================
            if (committed) {
                try {
                    fs.atomicReplaceBlockchainFile(blockchainName);
                } catch (Exception moveError) {
                    // Здесь ВАЖНО: мы уже не можем откатить БД.
                    // Оставляем tmp и даём наверх ошибку — клиент увидит internal_error,
                    // а ты при старте починишь файловую часть.
                    throw new SQLException(
                            "DB committed but file replace failed; tmp kept for recovery. blockchainName=" + blockchainName,
                            moveError
                    );
                }
            }
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
        // защита от переполнения long (маловероятно, но пусть будет)
        long r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new IllegalArgumentException("fileSizeBytes overflow: " + x + " + " + y);
        }
        return r;
    }
}