package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import server.logic.ws_protocol.WireCodes;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.BlockEntry;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;
import utils.blockchain.BlockchainNameUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;

/**
 * BlockchainStateService_new — атомарное добавление блока (НОВЫЙ формат):
 *  - decode Base64 -> FULL block bytes
 *  - parse block (recordSize must match)
 *  - взять loginKey (publicKey32) пользователя
 *  - взять prevGlobalHash / prevLineHash из DB-состояния
 *  - собрать preimage -> sha256 -> verify signature
 *  - вставить blocks
 *  - обновить blockchain_state: lastGlobalNumber/lastGlobalHash (и позже line stuff)
 *
 * Ответ наружу: только reasonCode + serverLastGlobalNumber/serverLastGlobalHash
 */
public final class BlockchainStateService {

    /** Результат атомарного addBlock */
    public static final class AddBlockResult {
        public final int httpStatus;                  // WireCodes.Status.*
        public final String reasonCode;               // null если ok
        public final int serverLastGlobalNumber;
        public final String serverLastGlobalHash;

        public AddBlockResult(int httpStatus, String reasonCode, int serverLastGlobalNumber, String serverLastGlobalHash) {
            this.httpStatus = httpStatus;
            this.reasonCode = reasonCode;
            this.serverLastGlobalNumber = serverLastGlobalNumber;
            this.serverLastGlobalHash = serverLastGlobalHash;
        }

        public boolean isOk() {
            return httpStatus == WireCodes.Status.OK;
        }
    }

    private static volatile BlockchainStateService instance;

    private final SqliteDbController db = SqliteDbController.getInstance();
    private final BlocksDAO blocksDAO = BlocksDAO.getInstance();
    private final BlockchainStateDAO stateDAO = BlockchainStateDAO.getInstance();
    private final SolanaUsersDAO solanaUsersDAO = SolanaUsersDAO.getInstance();

    private BlockchainStateService() {}

    public static BlockchainStateService getInstance() {
        if (instance == null) {
            synchronized (BlockchainStateService.class) {
                if (instance == null) instance = new BlockchainStateService();
            }
        }
        return instance;
    }

    public AddBlockResult addBlockAtomically(
            String login,
            String blockchainName,
            int globalNumber,
            String prevGlobalHashHex,
            String blockBytesB64
    ) {

        // Базовая валидация
        if (blockchainName == null || blockchainName.isBlank())
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "empty_blockchain_name", 0, "");

        // Можно быстро проверить, что login согласован с blockchainName (если хочешь строгость)
        String loginFromBlockchainName = BlockchainNameUtil.loginFromBlockchainName(blockchainName);
        if (loginFromBlockchainName == null || loginFromBlockchainName.isBlank())
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_blockchain_name", 0, "");


        //  todo действительно давай прото брать логин из имени блокчена и не передавать его отдельно в запросе!
        if (login == null || login.isBlank()) {
            // раз уж у нас есть loginFromName — можно принимать login пустым,
            // но ты явно передаёшь login, поэтому пока так:
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "empty_login", 0, "");
        }

        // (опционально) сверка
        if (!loginFromBlockchainName.equals(login)) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "login_not_match_blockchain_name", 0, "");
        }

        byte[] blockBytes;
        try {
            blockBytes = decodeBase64(blockBytesB64);
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_base64", 0, "");
        }

//  todo ну и ещё тут нужно проверить что не только сам формат блока верный, но и запись в этом блоке верная - тоесть что её можно распарсить!



        final BchBlockEntry block;
        try {
            block = new BchBlockEntry(blockBytes);
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_format", 0, "");
        }

        // Проверка, что глобальный номер совпадает
        if (block.recordNumber != globalNumber) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "global_number_mismatch", 0, "");
        }

        try (Connection c = db.getConnection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // 1) пользователь (ключ подписи берём из loginKey)
                SolanaUserEntry u = solanaUsersDAO.getByLogin(c, login);
                if (u == null) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.NOT_FOUND, "user_not_found", 0, "");
                }

                byte[] loginKey32 = u.getLoginKeyByte();
                if (loginKey32 == null || loginKey32.length != 32) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_user_login_key", 0, "");
                }

                // 2) состояние блокчейна
                BlockchainStateEntry st = stateDAO.getByBlockchainName(c, blockchainName);

                //todo тут надо учесть тот случай что если это 0 блок тоесть начало блокчейна то логично что ещё нет самого файла блокчейна и по этому нет и BlockchainStateEntry
                if (st == null) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.NOT_FOUND, "blockchain_state_not_found", 0, "");
                }

                // 3) проверяем последовательность глобального номера
                int expected = st.getLastGlobalNumber() + 1;
                if (globalNumber != expected) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_global_number", st.getLastGlobalNumber(), nn(st.getLastGlobalHash()));
                }

                // 4) prev hashes (пока line == global)
                byte[] prevGlobalHash32 = hexTo32(nn(prevGlobalHashHex));
                byte[] serverPrevGlobal32 = hexTo32(nn(st.getLastGlobalHash()));

                // чтобы не принимали «левый prev»:
                if (!bytesEq(prevGlobalHash32, serverPrevGlobal32)) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_prev_global_hash", st.getLastGlobalNumber(), nn(st.getLastGlobalHash()));
                }

                byte[] prevLineHash32 = prevGlobalHash32; // пока линии не используем
//todo точно так же как и глобальный проверяем преведущий хэш по линии.  он пока не используется, в том плане что у нас везде линия 0 и приведущий хэш по линии по сути равен приведущему глобальному хэшу, но его тоже надо проверять.
// по сути можно сказать он используется просто пока везде только одна линия с индексом 0


                // 5) крипто-проверка
                boolean ok = BchCryptoVerifier.verifyAll(
                        login,
                        prevGlobalHash32,
                        prevLineHash32,
                        block.getRawBytes(),
                        block.getSignature64(),
                        block.getHash32(),
                        loginKey32
                );

                if (!ok) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_signature_or_hash", st.getLastGlobalNumber(), nn(st.getLastGlobalHash()));
                }


                //todo сам код добавление блока вынести в отдельный класс - тк там надо потом усложнить действие
                // 6) вставка блока
                insertBlockRow(c, login, blockchainName, globalNumber, prevGlobalHashHex, blockBytes);

                // 7) обновление состояния — hash теперь = hash32 нового блока (HEX)
                String newHashHex = toHex(block.getHash32());
                st.setLastGlobalNumber(globalNumber);
                st.setLastGlobalHash(newHashHex);

                // линии пока равны глобалу
                st.setLastLineNumber(0, globalNumber);
                st.setLastLineHash(0, newHashHex);

                st.setUpdatedAtMs(System.currentTimeMillis());
                stateDAO.upsert(c, st);

                c.commit();
                return new AddBlockResult(WireCodes.Status.OK, null, st.getLastGlobalNumber(), nn(st.getLastGlobalHash()));

            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException ignore) {}
                return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "internal_error", 0, "");
            } finally {
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) {}
            }
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "db_error", 0, "");
        }
    }

    private void insertBlockRow(
            Connection c,
            String login,
            String blockchainName,
            int globalNumber,
            String prevGlobalHashHex,
            byte[] blockBytes
    ) throws SQLException {

        BlockEntry e = new BlockEntry();

        e.setLogin(login);
        e.setBchName(blockchainName);

        e.setBlockGlobalNumber(globalNumber);
        e.setBlockGlobalPreHashe(nn(prevGlobalHashHex));

        // линии пока не используем (равны глобалу)
        e.setBlockLineIndex(0);
        e.setBlockLineNumber(globalNumber);
        e.setBlockLinePreHashe(nn(prevGlobalHashHex));

        e.setMsgType(0);
        e.setBlockByte(blockBytes);

        // nullable ссылки (как ты просил ранее)
        e.setToLogin(null);
        e.setToBchName(null);
        e.setToBlockGlobalNumber(null);
        e.setToBlockHashe(null);

        blocksDAO.upsert(c, e);
    }

    // -------------------- utils --------------------

    private static String nn(String s) { return s == null ? "" : s; }

    private static byte[] decodeBase64(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("empty base64");
        return Base64.getDecoder().decode(s);
    }

    /** hex(64) -> 32 bytes; пустой -> 32 нуля */
    private static byte[] hexTo32(String hex) {
        if (hex == null || hex.isBlank()) return new byte[32];
        String h = hex.trim();
        if (h.length() != 64) throw new IllegalArgumentException("hex hash must be 64 chars");
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(h.charAt(i * 2), 16);
            int lo = Character.digit(h.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad hex");
            out[i] = (byte)((hi << 4) | lo);
        }
        return out;
    }

    private static boolean bytesEq(byte[] a, byte[] b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        int x = 0;
        for (int i = 0; i < a.length; i++) x |= (a[i] ^ b[i]);
        return x == 0;
    }

    private static String toHex(byte[] bytes) {
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}