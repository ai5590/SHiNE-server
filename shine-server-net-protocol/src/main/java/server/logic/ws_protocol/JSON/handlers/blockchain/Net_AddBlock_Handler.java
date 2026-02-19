package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import blockchain.MsgSubType;
import blockchain.body.BodyHasLine;
import blockchain.body.BodyHasTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.Base64Ws;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Exception_Response;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.blockchain.Net_AddBlock_Handler_utils.BlockchainLocks;
import server.logic.ws_protocol.JSON.handlers.blockchain.Net_AddBlock_Handler_utils.BlockchainWriter;
import server.logic.ws_protocol.JSON.handlers.blockchain.entyties.Net_AddBlock_Request;
import server.logic.ws_protocol.JSON.handlers.blockchain.entyties.Net_AddBlock_Response;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.BlockEntry;
import utils.blockchain.BlockchainNameUtil;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Net_AddBlock_Handler — единый хэндлер добавления блока (JSON).
 *
 * Изменение (v3):
 * - ВСЕ ошибки теперь возвращаются в стандартном формате Net_Exception_Response:
 *   status != 200, payload: { code, message, serverLastGlobalNumber, serverLastGlobalHash }
 * - Успех — как и раньше Net_AddBlock_Response (status=200).
 */
public final class Net_AddBlock_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_AddBlock_Handler.class);

    private final BlocksDAO blocksDAO = BlocksDAO.getInstance();
    private final BlockchainStateDAO stateDAO = BlockchainStateDAO.getInstance();

    private final BlockchainWriter dbWriter = new BlockchainWriter(blocksDAO, stateDAO);

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) {

        Net_AddBlock_Request req = (Net_AddBlock_Request) baseReq;

        String blockchainName = req.getBlockchainName();
        ReentrantLock lock = BlockchainLocks.lockFor(blockchainName);
        lock.lock();
        try {
            AddBlockResult r = addBlock(
                    blockchainName,
                    req.getBlockNumber(),        // старое поле, пока оставляем
                    req.getPrevBlockHash(),      // старое поле, пока оставляем
                    req.getBlockBytesB64()
            );

            // ✅ УСПЕХ: как раньше
            if (r.isOk()) {
                Net_AddBlock_Response resp = new Net_AddBlock_Response();
                resp.setOp(req.getOp());
                resp.setRequestId(req.getRequestId());
                resp.setStatus(WireCodes.Status.OK);

                resp.setReasonCode(null);
                resp.setServerLastGlobalNumber(r.serverLastBlockNumber);
                resp.setServerLastGlobalHash(r.serverLastBlockHashHex);

                return resp;
            }

            // ✅ ОШИБКА: стандартный формат (code + message) + доп.поля для ресинка
            return error(req, r.httpStatus, r.reasonCode, r.serverLastBlockNumber, r.serverLastBlockHashHex);

        } finally {
            lock.unlock();
        }
    }

    private Net_Response error(Net_AddBlock_Request req,
                               int status,
                               String reasonCode,
                               int serverLastNum,
                               String serverLastHashHex) {

        AddBlockExceptionResponse resp = new AddBlockExceptionResponse();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(status);

        // code — машинный
        resp.setCode(reasonCode != null ? reasonCode : "add_block_error");
        // message — человеческий (можешь улучшать тексты как угодно)
        resp.setMessage(humanMessage(reasonCode));

        // полезно клиенту для ресинка
        resp.setServerLastGlobalNumber(serverLastNum);
        resp.setServerLastGlobalHash(serverLastHashHex);

        return resp;
    }

    private static String humanMessage(String code) {
        if (code == null) return "Ошибка добавления блока";

        return switch (code) {
            case "empty_blockchain_name" -> "Пустое имя блокчейна";
            case "bad_blockchain_name" -> "Некорректное имя блокчейна";
            case "db_error" -> "Ошибка базы данных";
            case "blockchain_state_not_found" -> "Состояние блокчейна не найдено";
            case "state_last_hash_invalid" -> "Повреждено состояние блокчейна: неверный last_block_hash";
            case "bad_block_base64" -> "Некорректный base64 блока";
            case "limit_exceeded" -> "Превышен лимит размера блокчейна";
            case "limit_check_failed" -> "Ошибка проверки лимита размера";
            case "bad_block_format" -> "Некорректный формат блока";
            case "bad_block_body" -> "Некорректное тело блока";
            case "bad_block_number" -> "Некорректный номер блока";
            case "req_global_mismatch" -> "Номер блока в запросе не совпадает с номером в блоке";
            case "bad_prev_hash" -> "Некорректный prevHash (цепочка не совпадает)";
            case "bad_blockchain_key_len" -> "Некорректный ключ блокчейна в состоянии (ожидалось 32 байта)";
            case "signature_verify_failed" -> "Ошибка проверки подписи блока";
            case "bad_signature" -> "Некорректная подпись блока";
            case "prev_line_block_not_found" -> "Не найден блок prevLineNumber для проверки линии";
            case "bad_prev_line_hash" -> "Некорректный prevLineHash";
            case "db_error_prev_line_check" -> "Ошибка БД при проверке prevLine";
            case "internal_error" -> "Внутренняя ошибка сервера при записи блока";
            default -> "Ошибка: " + code;
        };
    }

    private AddBlockResult addBlock(
            String blockchainName,
            int globalNumberFromReq,
            String prevGlobalHashHexFromReq,
            String blockBytesB64
    ) {
        if (blockchainName == null || blockchainName.isBlank()) {
            log.warn("AddBlock: пустой blockchainName (reqGlobalNumber={})", globalNumberFromReq);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "empty_blockchain_name", 0, "");
        }

        String login = BlockchainNameUtil.loginFromBlockchainName(blockchainName);
        if (login == null || login.isBlank()) {
            log.warn("AddBlock: плохой blockchainName='{}' => login не получился (reqGlobalNumber={})",
                    blockchainName, globalNumberFromReq);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_blockchain_name", 0, "");
        }

        // 1) state обязателен
        final BlockchainStateEntry st;
        try {
            st = stateDAO.getByBlockchainName(blockchainName);
        } catch (Exception e) {
            log.error("AddBlock: ошибка БД при чтении blockchain_state (login={}, blockchainName={}, reqGlobalNumber={})",
                    login, blockchainName, globalNumberFromReq, e);
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "db_error", 0, "");
        }

        if (st == null) {
            log.warn("AddBlock: blockchain_state_not_found (login={}, blockchainName={}, reqGlobalNumber={})",
                    login, blockchainName, globalNumberFromReq);
            return new AddBlockResult(WireCodes.Status.NOT_FOUND, "blockchain_state_not_found", -1, "");
        }

        final int serverLastNum = st.getLastBlockNumber();

        final byte[] serverLastHash32;
        try {
            serverLastHash32 = (serverLastNum < 0)
                    ? new byte[32]
                    : require32OrThrow(st.getLastBlockHash(), "state.last_block_hash is null/invalid");
        } catch (Exception e) {
            // ✅ Раньше тут мог вылететь неожиданный 500 через внешний try/catch.
            log.error("AddBlock: state_last_hash_invalid (login={}, blockchainName={}, serverLastNum={})",
                    login, blockchainName, serverLastNum, e);
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "state_last_hash_invalid", serverLastNum, "");
        }

        final String serverLastHashHex = toHex(serverLastHash32);

        // 2) decode block
        final byte[] blockBytes;
        try {
            blockBytes = decodeBase64(blockBytesB64);
        } catch (Exception e) {
            log.warn("AddBlock: некорректный base64 блока (login={}, blockchainName={}, reqGlobalNumber={})",
                    login, blockchainName, globalNumberFromReq, e);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_base64", serverLastNum, serverLastHashHex);
        }

        // 3) лимит (оставляем как было)
        try {
            long oldSize = st.getFileSizeBytes();
            long limit = st.getSizeLimit();
            long newSize = safeAdd(oldSize, blockBytes.length);

            if (limit > 0 && newSize > limit) {
                log.warn("AddBlock: limit_exceeded (login={}, blockchainName={}, oldSize={}, addLen={}, newSize={}, limit={})",
                        login, blockchainName, oldSize, blockBytes.length, newSize, limit);
                return new AddBlockResult(413, "limit_exceeded", serverLastNum, serverLastHashHex);
            }
        } catch (Exception e) {
            log.error("AddBlock: limit_check_failed (login={}, blockchainName={})", login, blockchainName, e);
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "limit_check_failed", serverLastNum, serverLastHashHex);
        }

        // 4) parse block
        final BchBlockEntry block;
        try {
            block = new BchBlockEntry(blockBytes);
        } catch (Exception e) {
            log.warn("AddBlock: не удалось распарсить BchBlockEntry (login={}, blockchainName={}, bytesLen={})",
                    login, blockchainName, blockBytes.length, e);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_format", serverLastNum, serverLastHashHex);
        }

        // body.check()
        try {
            block.body.check();
        } catch (Exception e) {
            log.warn("AddBlock: body.check() не прошёл (login={}, blockchainName={}, blockNumber={}, type={}, ver={})",
                    login, blockchainName, block.blockNumber, (block.type & 0xFFFF), (block.version & 0xFFFF), e);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_body", serverLastNum, serverLastHashHex);
        }

        // 4.2) запрет дырок: blockNumber строго last+1
        int expectedBlockNumber = serverLastNum + 1;
        if (block.blockNumber != expectedBlockNumber) {
            log.warn("AddBlock: bad_block_number (login={}, blockchainName={}, пришёл={}, ожидали={}, serverLastNum={})",
                    login, blockchainName, block.blockNumber, expectedBlockNumber, serverLastNum);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_number", serverLastNum, serverLastHashHex);
        }

        // (временная совместимость) req.globalNumber должен совпасть с block.blockNumber
        if (globalNumberFromReq != block.blockNumber) {
            log.warn("AddBlock: req_global_mismatch (login={}, blockchainName={}, reqGlobal={}, blockNumber={})",
                    login, blockchainName, globalNumberFromReq, block.blockNumber);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "req_global_mismatch", serverLastNum, serverLastHashHex);
        }

        // 4.3) проверка цепочки по prevHash32
        if (!Arrays.equals(block.prevHash32, serverLastHash32)) {
            log.warn("AddBlock: bad_prev_hash (login={}, blockchainName={}, blockNumber={}, clientPrev={}, serverPrev={})",
                    login, blockchainName, block.blockNumber, toHex(block.prevHash32), serverLastHashHex);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_prev_hash", serverLastNum, serverLastHashHex);
        }

        // 5) pubKey
        final byte[] pubKey32 = st.getBlockchainKeyBytes();
        if (pubKey32 == null || pubKey32.length != 32) {
            log.warn("AddBlock: bad_blockchain_key_len (login={}, blockchainName={}, blockNumber={}, keyLen={})",
                    login, blockchainName, block.blockNumber, (pubKey32 == null ? -1 : pubKey32.length));
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_blockchain_key_len", serverLastNum, serverLastHashHex);
        }

        // 6) подпись по hash32(preimage)
        boolean sigOk;
        try {
            sigOk = BchCryptoVerifier.verifyBlock(block, pubKey32);
        } catch (Exception e) {
            log.warn("AddBlock: signature_verify_failed (login={}, blockchainName={}, blockNumber={})",
                    login, blockchainName, block.blockNumber, e);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "signature_verify_failed", serverLastNum, serverLastHashHex);
        }

        if (!sigOk) {
            log.warn("AddBlock: bad_signature (login={}, blockchainName={}, blockNumber={})",
                    login, blockchainName, block.blockNumber);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_signature", serverLastNum, serverLastHashHex);
        }

        // 7) line columns (only for BodyHasLine)
        Integer lineCode = null;
        Integer prevLineNumber = null;
        byte[] prevLineHash32 = null;
        Integer thisLineNumber = null;

        if (block.body instanceof BodyHasLine bl) {
            lineCode = bl.lineCode();
            prevLineNumber = bl.prevLineBlockGlobalNumber();
            prevLineHash32 = bl.prevLineBlockHash32();
            thisLineNumber = bl.lineSeq();

            // Нормализация: -1 не пишем в БД (для совместимости со старым TextBody)
            if (prevLineNumber != null && prevLineNumber == -1) {
                prevLineNumber = null;
                prevLineHash32 = null;
                thisLineNumber = null;
            }

            // Если prevLineNumber задан — проверяем его хэш
            if (prevLineNumber != null) {
                try {
                    byte[] dbPrevHash = blocksDAO.getHashByNumber(blockchainName, prevLineNumber);
                    if (dbPrevHash == null) {
                        log.warn("AddBlock: prev_line_block_not_found (login={}, blockchainName={}, blockNumber={}, prevLineNumber={})",
                                login, blockchainName, block.blockNumber, prevLineNumber);
                        return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "prev_line_block_not_found", serverLastNum, serverLastHashHex);
                    }
                    if (!Arrays.equals(dbPrevHash, require32OrThrow(prevLineHash32, "prevLineHash32 invalid"))) {
                        log.warn("AddBlock: bad_prev_line_hash (login={}, blockchainName={}, blockNumber={}, prevLineNumber={})",
                                login, blockchainName, block.blockNumber, prevLineNumber);
                        return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_prev_line_hash", serverLastNum, serverLastHashHex);
                    }
                } catch (Exception e) {
                    log.error("AddBlock: db_error_prev_line_check (login={}, blockchainName={}, blockNumber={})",
                            login, blockchainName, block.blockNumber, e);
                    return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "db_error_prev_line_check", serverLastNum, serverLastHashHex);
                }
            }
        }

        // 8) сформировать запись и записать (DB + state + файл)
        try {
            BlockEntry be = new BlockEntry();
            be.setLogin(login);
            be.setBchName(blockchainName);

            be.setBlockNumber(block.blockNumber);
            be.setMsgType(block.type & 0xFFFF);
            be.setMsgSubType(block.subType & 0xFFFF);

            be.setBlockBytes(block.toBytes());
            be.setBlockHash(block.getHash32());
            be.setBlockSignature(block.getSignature64());

            // line columns (optional)
            be.setLineCode(lineCode);
            be.setPrevLineNumber(prevLineNumber);
            be.setPrevLineHash(prevLineHash32);
            be.setThisLineNumber(thisLineNumber);

            // target columns (optional)
            if (block.body instanceof BodyHasTarget t) {
                be.setToLogin(t.toLogin());
                be.setToBchName(t.toBchName());
                be.setToBlockNumber(t.toBlockGlobalNumber());
                be.setToBlockHash(t.toBlockHashBytes());
            }

            // edit helper (optional): если TEXT_EDIT_* — это "редактирование блока цели"
            int type = block.type & 0xFFFF;
            int sub = block.subType & 0xFFFF;

            if (type == 1
                    && (sub == (MsgSubType.TEXT_EDIT_POST & 0xFFFF) || sub == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF))
                    && be.getToBlockNumber() != null) {
                be.setEditedByBlockNumber(be.getToBlockNumber());
            }

            dbWriter.appendBlockAndState(blockchainName, block, st, be);

        } catch (Exception e) {
            log.error("AddBlock: внутренняя ошибка при записи блока (login={}, blockchainName={}, blockNumber={})",
                    login, blockchainName, block.blockNumber, e);
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "internal_error", serverLastNum, serverLastHashHex);
        }

        String newHashHex = toHex(block.getHash32());

        log.info("✅ AddBlock ok: login={}, blockchainName={}, blockNumber={}, newHash={}",
                login, blockchainName, block.blockNumber, newHashHex);

        return new AddBlockResult(WireCodes.Status.OK, null, block.blockNumber, newHashHex);
    }

    /* ===================================================================== */
    /* ====================== Helpers ====================================== */
    /* ===================================================================== */

    private static byte[] decodeBase64(String b64) {
        if (b64 == null) throw new IllegalArgumentException("blockBytesB64 == null");
        return Base64Ws.decode(b64);
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) throw new ArithmeticException("long overflow");
        return r;
    }

    private static byte[] require32OrThrow(byte[] b, String msg) {
        if (b == null || b.length != 32) throw new IllegalArgumentException(msg);
        return b;
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null) return "null";
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    /**
     * Спец-ответ ошибки AddBlock: стандартный code/message + поля для ресинка.
     * В wire-формате это окажется внутри payload.
     */
    public static final class AddBlockExceptionResponse extends Net_Exception_Response {
        private Integer serverLastGlobalNumber;
        private String serverLastGlobalHash;

        public Integer getServerLastGlobalNumber() {
            return serverLastGlobalNumber;
        }

        public void setServerLastGlobalNumber(Integer serverLastGlobalNumber) {
            this.serverLastGlobalNumber = serverLastGlobalNumber;
        }

        public String getServerLastGlobalHash() {
            return serverLastGlobalHash;
        }

        public void setServerLastGlobalHash(String serverLastGlobalHash) {
            this.serverLastGlobalHash = serverLastGlobalHash;
        }
    }

    private static final class AddBlockResult {
        final int httpStatus;
        final String reasonCode;
        final int serverLastBlockNumber;
        final String serverLastBlockHashHex;

        AddBlockResult(int httpStatus, String reasonCode, int serverLastBlockNumber, String serverLastBlockHashHex) {
            this.httpStatus = httpStatus;
            this.reasonCode = reasonCode;
            this.serverLastBlockNumber = serverLastBlockNumber;
            this.serverLastBlockHashHex = serverLastBlockHashHex;
        }

        boolean isOk() { return httpStatus == WireCodes.Status.OK; }
    }
}
