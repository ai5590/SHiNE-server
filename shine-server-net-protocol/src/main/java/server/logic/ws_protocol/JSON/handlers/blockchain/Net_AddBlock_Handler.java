package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.entyties.blockchain.Net_AddBlock_Request;
import server.logic.ws_protocol.JSON.entyties.blockchain.Net_AddBlock_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;
import utils.blockchain.BlockchainNameUtil;

import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Net_AddBlock_Handler — единый хэндлер добавления блока (JSON).
 *
 * Задачи:
 *  1) Лочим добавление блоков для конкретного blockchainName (защита от гонок в одном процессе).
 *  2) Декодируем блок из Base64 и парсим его структуру.
 *  3) Парсим body и валидируем (type/version + содержимое).
 *  4) Проверяем globalNumber и prevGlobalHash относительно server state.
 *  5) Проверяем подпись/хэш (Ed25519 над hash32, hash32=sha256(preimage)).
 *  6) Делаем запись в БД через BlockchainDbWriter (атомарность реализуется там).
 *  7) Возвращаем клиенту serverLastGlobalNumber/serverLastGlobalHash.
 */
public final class Net_AddBlock_Handler implements JsonMessageHandler {

    // DAO (перегрузки сами создают/закрывают Connection внутри)
    private final BlocksDAO blocksDAO = BlocksDAO.getInstance();
    private final BlockchainStateDAO stateDAO = BlockchainStateDAO.getInstance();
    private final SolanaUsersDAO solanaUsersDAO = SolanaUsersDAO.getInstance();

    // Writer отвечает за транзакции/атомарность и консистентность БД
    private final BlockchainWriter dbWriter = new BlockchainWriter(blocksDAO, stateDAO);

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) {

        Net_AddBlock_Request req = (Net_AddBlock_Request) baseReq;

        // 0) Берём имя цепочки и лочим операции добавления для неё
        String blockchainName = req.getBlockchainName();
        ReentrantLock lock = BlockchainLocks.lockFor(blockchainName);
        lock.lock();
        try {
            AddBlockResult r = addBlock(blockchainName,
                    req.getGlobalNumber(),
                    req.getPrevGlobalHash(),
                    req.getBlockBytesB64());

            // 7) Формируем стандартный Net_AddBlock_Response
            Net_AddBlock_Response resp = new Net_AddBlock_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());

            if (r.isOk()) {
                resp.setStatus(WireCodes.Status.OK);
                resp.setReasonCode(null);
            } else {
                resp.setStatus(r.httpStatus);
                resp.setReasonCode(r.reasonCode);
            }

            // Возвращаем актуальное состояние сервера (даже при ошибках, где уместно)
            resp.setServerLastGlobalNumber(r.serverLastGlobalNumber);
            if (r.serverLastGlobalHash != null) {
                resp.setServerLastGlobalHash(r.serverLastGlobalHash);
            }

            return resp;

        } finally {
            lock.unlock();
        }
    }

    /* ===================================================================== */
    /* ========================== Основная логика =========================== */
    /* ===================================================================== */

    /**
     * Внутренняя логика добавления блока (без ручного управления Connection/tx).
     * Все атомарные записи — внутри BlockchainDbWriter.
     */
    private AddBlockResult addBlock(
            String blockchainName,
            int globalNumber,
            String prevGlobalHashHex,
            String blockBytesB64
    ) {
        // 1) Быстрая валидация входных параметров
        if (blockchainName == null || blockchainName.isBlank()) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "empty_blockchain_name", 0, "");
        }

        // 2) Из имени блокчейна вытаскиваем login (как ты и хотел — через util)
        String login = BlockchainNameUtil.loginFromBlockchainName(blockchainName);
        if (login == null || login.isBlank()) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_blockchain_name", 0, "");
        }

        // 3) Декодируем блок из Base64
        final byte[] blockBytes;
        try {
            blockBytes = decodeBase64(blockBytesB64);
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_base64", 0, "");
        }

        // 4) Парсим блок (проверяется recordSize и минимальная длина)
        final BchBlockEntry block;
        try {
            block = new BchBlockEntry(blockBytes);
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_format", 0, "");
        }

        // 5) Валидируем body (type/version + содержимое) — теперь body уже распарсен внутри BchBlockEntry
        try {
            block.body.check();
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_body", 0, "");
        }

        // 6) Защита от рассинхрона: recordNumber внутри блока должен совпадать с заявленным globalNumber
        if (block.recordNumber != globalNumber) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "global_number_mismatch", 0, "");
        }

        // 7) Получаем пользователя и его loginKey (публичный ключ 32 байта)
        SolanaUserEntry u;
        try {
            u = solanaUsersDAO.getByLogin(login); // перегрузка: сама открывает/закрывает соединение
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "db_error", 0, "");
        }

        if (u == null) {
            return new AddBlockResult(WireCodes.Status.NOT_FOUND, "user_not_found", 0, "");
        }

        byte[] loginKey32 = u.getLoginKeyByte();
        if (loginKey32 == null || loginKey32.length != 32) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_user_login_key", 0, "");
        }

        // 8) Читаем текущее состояние блокчейна с сервера
        BlockchainStateEntry st;
        try {
            st = stateDAO.getByBlockchainName(blockchainName); // перегрузка: сама открывает/закрывает соединение
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "db_error", 0, "");
        }

        // 9) Определяем serverLastNum/serverLastHash (если state ещё нет — ожидаем genesis с globalNumber=0)
        final int serverLastNum;
        final String serverLastHash;
        if (st == null) {
            if (globalNumber != 0) {
                return new AddBlockResult(WireCodes.Status.NOT_FOUND, "blockchain_state_not_found", 0, "");
            }
            serverLastNum = -1;
            serverLastHash = "";
        } else {
            serverLastNum = st.getLastGlobalNumber();
            serverLastHash = nn(st.getLastGlobalHash());
        }

        // 10) Проверяем, что клиент присылает следующий блок ровно (last+1)
        int expected = serverLastNum + 1;
        if (globalNumber != expected) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_global_number", serverLastNum, serverLastHash);
        }

        // 11) Проверяем prevGlobalHash: клиент должен ссылаться на текущий serverLastHash
        final byte[] prevGlobalHash32;
        final byte[] serverPrevGlobal32;
        try {
            prevGlobalHash32 = hexTo32(nn(prevGlobalHashHex));
            serverPrevGlobal32 = (st == null) ? new byte[32] : hexTo32(nn(st.getLastGlobalHash()));
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_prev_global_hash_format", serverLastNum, serverLastHash);
        }

        if (!bytesEq(prevGlobalHash32, serverPrevGlobal32)) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_prev_global_hash", serverLastNum, serverLastHash);
        }

        // 12) Пока линии не используем — prevLineHash равен prevGlobalHash (как ты писал)
        byte[] prevLineHash32 = prevGlobalHash32;

        // 13) Криптопроверка: hash в блоке + подпись над hash
        boolean ok = BchCryptoVerifier.verifyAll(
                login,
                prevGlobalHash32,
                prevLineHash32,
                block.getRawBytes(),       // только RAW (без signature/hash)
                block.getSignature64(),    // подпись Ed25519
                loginKey32,                // public key пользователя
                block.getHash32()          // ожидаемый hash32 из самого блока
        );

        if (!ok) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_signature_or_hash", serverLastNum, serverLastHash);
        }

        // 14) Новый hash блока (hex) — то, что будет записано как lastGlobalHash
        String newHashHex = toHex(block.getHash32());

        // 15) Запись блока + обновление состояния (атомарность/транзакции — внутри dbWriter)
        try {
            dbWriter.appendBlockAndState(
                    login,
                    blockchainName,
                    nn(prevGlobalHashHex),
                    block,           // ✅ передаём целиком объект блока
                    st,
                    newHashHex
            );
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "internal_error", serverLastNum, serverLastHash);
        }

        // 16) Успех
        return new AddBlockResult(WireCodes.Status.OK, null, globalNumber, newHashHex);
    }

    /* ===================================================================== */
    /* ============================= Result ================================= */
    /* ===================================================================== */

    /** Результат обработки addBlock */
    private static final class AddBlockResult {
        final int httpStatus;                  // WireCodes.Status.*
        final String reasonCode;               // null если ok
        final int serverLastGlobalNumber;
        final String serverLastGlobalHash;

        AddBlockResult(int httpStatus, String reasonCode, int serverLastGlobalNumber, String serverLastGlobalHash) {
            this.httpStatus = httpStatus;
            this.reasonCode = reasonCode;
            this.serverLastGlobalNumber = serverLastGlobalNumber;
            this.serverLastGlobalHash = serverLastGlobalHash;
        }

        boolean isOk() {
            return httpStatus == WireCodes.Status.OK;
        }
    }

    /* ===================================================================== */
    /* ============================== Utils ================================= */
    /* ===================================================================== */

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