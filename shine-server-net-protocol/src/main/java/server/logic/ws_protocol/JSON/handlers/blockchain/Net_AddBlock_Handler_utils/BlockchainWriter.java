// =======================
// server/logic/ws_protocol/JSON/handlers/blockchain/Net_AddBlock_Handler_utils/BlockchainWriter.java
// (НОВАЯ ВЕРСИЯ — чтобы AddBlock работал с новым blocks/state)
// =======================
package server.logic.ws_protocol.JSON.handlers.blockchain.Net_AddBlock_Handler_utils;

import blockchain.BchBlockEntry;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.BlockEntry;
import utils.files.FileStoreUtil;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * BlockchainWriter — запись блока в DB + обновление state + запись в файл.
 *
 * ВАЖНО:
 * - Это минимальный рабочий вариант под новый формат.
 * - Если у тебя уже есть "атомарность" сложнее (tmp_bch + commit/recovery) — можно усилить потом.
 */
public final class BlockchainWriter {

    private final BlocksDAO blocksDAO;
    private final BlockchainStateDAO stateDAO;
    private final FileStoreUtil fs = FileStoreUtil.getInstance();

    public BlockchainWriter(BlocksDAO blocksDAO, BlockchainStateDAO stateDAO) {
        this.blocksDAO = blocksDAO;
        this.stateDAO = stateDAO;
    }

    public void appendBlockAndState(String blockchainName,
                                    BchBlockEntry block,
                                    BlockchainStateEntry st,
                                    BlockEntry be) throws SQLException {

        long nowMs = System.currentTimeMillis();

        try (Connection c = shine.db.SqliteDbController.getInstance().getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1) insert block
                blocksDAO.insert(c, be);

                // 2) update state
                st.setLastBlockNumber(block.blockNumber);
                st.setLastBlockHash(block.getHash32());
                st.setFileSizeBytes(st.getFileSizeBytes() + block.toBytes().length);
                st.setUpdatedAtMs(nowMs);

                stateDAO.upsert(c, st);

                c.commit();
            } catch (Exception e) {
                try { c.rollback(); } catch (Exception ignored) {}
                if (e instanceof SQLException se) throw se;
                throw new SQLException("appendBlockAndState failed", e);
            } finally {
                try { c.setAutoCommit(true); } catch (Exception ignored) {}
            }
        }

        // 3) append to file (минимально: просто дописать)
        // Если у тебя уже есть логика tmp_bch+atomicReplace — можно заменить тут.
        String fileName = fs.buildBlockchainFileName(blockchainName);
        fs.addDataToFile(fileName, block.toBytes());
    }
}