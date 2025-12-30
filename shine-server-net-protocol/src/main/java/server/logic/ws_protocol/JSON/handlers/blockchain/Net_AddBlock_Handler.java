package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.entyties.blockchain.Net_AddBlock_Request;
import server.logic.ws_protocol.JSON.entyties.blockchain.Net_AddBlock_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.entities.BlockchainStateEntry;
import utils.blockchain.BlockchainNameUtil;

import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Net_AddBlock_Handler — единый хэндлер добавления блока (JSON).
 *
 * Задачи:
 *  1) Лочим добавление блоков для конкретного blockchainName (защита от гонок в одном процессе).
 *  2) Декодируем блок из Base64 и парсим его структуру.
 *  3) Валидируем body (type/version + содержимое).
 *  4) Проверяем globalNumber и prevGlobalHash относительно server state.
 *  5) Проверяем линии:
 *     - genesis: global=0, lineIndex=0, lineNumber=0
 *     - остальные: lineIndex=1..7, lineNumber по счётчику линии
 *  6) Проверяем подпись/хэш (Ed25519 над hash32, hash32=sha256(preimage)).
 *     preimage включает prevLineHash32 (берём из state по lineIndex).
 *  7) Пишем в БД+файл через BlockchainWriter (атомарность там).
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
                    req.getGlobalNumber(),
                    req.getPrevGlobalHash(),
                    req.getBlockBytesB64()
            );

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

            resp.setServerLastGlobalNumber(r.serverLastGlobalNumber);
            if (r.serverLastGlobalHash != null) {
                resp.setServerLastGlobalHash(r.serverLastGlobalHash);
            }

            return resp;

        } finally {
            lock.unlock();
        }
    }

    private AddBlockResult addBlock(
            String blockchainName,
            int globalNumber,
            String prevGlobalHashHex,
            String blockBytesB64
    ) {
        if (blockchainName == null || blockchainName.isBlank()) {
            log.warn("AddBlock: пустой blockchainName (globalNumber={})", globalNumber);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "empty_blockchain_name", 0, "");
        }

        String login = BlockchainNameUtil.loginFromBlockchainName(blockchainName);
        if (login == null || login.isBlank()) {
            log.warn("AddBlock: плохой blockchainName='{}' => login не получился (globalNumber={})",
                    blockchainName, globalNumber);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_blockchain_name", 0, "");
        }

        // -------------------------------------------------------------------
        // ✅ 1) state теперь ОБЯЗАТЕЛЕН (и ключ подписи берём из него)
        // -------------------------------------------------------------------
        final BlockchainStateEntry st;
        try {
            st = stateDAO.getByBlockchainName(blockchainName);
        } catch (Exception e) {
            log.error("AddBlock: ошибка БД при чтении blockchain_state (login={}, blockchainName={}, globalNumber={})",
                    login, blockchainName, globalNumber, e);
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "db_error", 0, "");
        }

        if (st == null) {
            // теперь даже для genesis это ошибка: state должен быть создан заранее (с lastGlobalNumber=-1)
            log.warn("AddBlock: blockchain_state_not_found (login={}, blockchainName={}, globalNumber={})",
                    login, blockchainName, globalNumber);
            return new AddBlockResult(WireCodes.Status.NOT_FOUND, "blockchain_state_not_found", -1, "");
        }

        final int serverLastNum = st.getLastGlobalNumber();
        final String serverLastHash = nn(st.getLastGlobalHash());

        // ✅ для genesis ожидаем, что state уже в начальном состоянии (-1)
        if (globalNumber == 0 && serverLastNum != -1) {
            log.warn("AddBlock: genesis_but_state_not_initial (login={}, blockchainName={}, stateLastGlobalNumber={})",
                    login, blockchainName, serverLastNum);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "genesis_but_state_not_initial", serverLastNum, serverLastHash);
        }

        // следующий global строго
        int expectedGlobal = serverLastNum + 1;
        if (globalNumber != expectedGlobal) {
            log.warn("AddBlock: bad_global_number (login={}, blockchainName={}, пришёл={}, ожидали={}, serverLastNum={}, serverLastHash={})",
                    login, blockchainName, globalNumber, expectedGlobal, serverLastNum, serverLastHash);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_global_number", serverLastNum, serverLastHash);
        }

        // -------------------------------------------------------------------
        // ✅ 2) Декодируем блок (раньше парсинга body)
        // -------------------------------------------------------------------
        final byte[] blockBytes;
        try {
            blockBytes = decodeBase64(blockBytesB64);
        } catch (Exception e) {
            log.warn("AddBlock: некорректный base64 блока (login={}, blockchainName={}, globalNumber={})",
                    login, blockchainName, globalNumber, e);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_base64", serverLastNum, serverLastHash);
        }

        // -------------------------------------------------------------------
        // ✅ 3) Ранняя проверка лимита ДО любых записей (как ты попросил)
        // -------------------------------------------------------------------
        try {
            long oldSize = st.getFileSizeBytes();
            long limit = st.getSizeLimit(); // предполагается, что поле уже есть (size_limit)
            long newSize = safeAdd(oldSize, blockBytes.length);

            if (limit > 0 && newSize > limit) {
                log.warn("AddBlock: limit_exceeded (login={}, blockchainName={}, globalNumber={}, oldSize={}, addLen={}, newSize={}, limit={})",
                        login, blockchainName, globalNumber, oldSize, blockBytes.length, newSize, limit);
                return new AddBlockResult(413, "limit_exceeded", serverLastNum, serverLastHash);
            }
        } catch (Exception e) {
            log.error("AddBlock: limit_check_failed (login={}, blockchainName={}, globalNumber={})",
                    login, blockchainName, globalNumber, e);
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "limit_check_failed", serverLastNum, serverLastHash);
        }

        // -------------------------------------------------------------------
        // ✅ 4) Парсим блок
        // -------------------------------------------------------------------
        final BchBlockEntry block;
        try {
            block = new BchBlockEntry(blockBytes);
        } catch (Exception e) {
            // важно: BchBlockEntry теперь сам валит блок, если body в неправильной линии
            log.warn("AddBlock: не удалось распарсить BchBlockEntry (login={}, blockchainName={}, globalNumber={}, bytesLen={})",
                    login, blockchainName, globalNumber, blockBytes.length, e);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_format", serverLastNum, serverLastHash);
        }

        // body.check()
        try {
            block.body.check();
        } catch (Exception e) {
            log.warn("AddBlock: body.check() не прошёл (login={}, blockchainName={}, globalNumber={}, bodyType={}, bodyVersion={})",
                    login, blockchainName, globalNumber, safeBodyType(block), safeBodyVersion(block), e);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_body", serverLastNum, serverLastHash);
        }

        // recordNumber == globalNumber
        if (block.recordNumber != globalNumber) {
            log.warn("AddBlock: global_number_mismatch (login={}, blockchainName={}, заявлен={}, внутриБлока={})",
                    login, blockchainName, globalNumber, block.recordNumber);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "global_number_mismatch", serverLastNum, serverLastHash);
        }

        // -------------------------------------------------------------------
        // ✅ 5) Ключ подписи берём из blockchain_state.blockchainKey (Base64(32))
        // -------------------------------------------------------------------
        final byte[] loginKey32;
        try {
            // предполагается, что st.getBlockchainKey() возвращает base64-строку, а getBlockchainKeyByte() -> 32 bytes
            loginKey32 = st.getBlockchainKeyBytes();
        } catch (Exception e) {
            log.warn("AddBlock: bad_blockchain_key_in_state (login={}, blockchainName={}, globalNumber={})",
                    login, blockchainName, globalNumber, e);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_blockchain_key_in_state", serverLastNum, serverLastHash);
        }

        if (loginKey32 == null || loginKey32.length != 32) {
            log.warn("AddBlock: bad_blockchain_key_len (login={}, blockchainName={}, globalNumber={}, keyLen={})",
                    login, blockchainName, globalNumber, (loginKey32 == null ? -1 : loginKey32.length));
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_blockchain_key_len", serverLastNum, serverLastHash);
        }

        // -------------------------------------------------------------------
        // ✅ 6) prevGlobalHash сравниваем со state.lastGlobalHash
        // -------------------------------------------------------------------
        final byte[] prevGlobalHash32;
        final byte[] serverPrevGlobal32;
        try {
            prevGlobalHash32 = hexTo32(nn(prevGlobalHashHex));
            serverPrevGlobal32 = hexTo32(nn(st.getLastGlobalHash())); // если пусто -> 32 нуля
        } catch (Exception e) {
            log.warn("AddBlock: bad_prev_global_hash_format (login={}, blockchainName={}, globalNumber={}, prevGlobalHashHex='{}')",
                    login, blockchainName, globalNumber, nn(prevGlobalHashHex), e);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_prev_global_hash_format", serverLastNum, serverLastHash);
        }

        if (!bytesEq(prevGlobalHash32, serverPrevGlobal32)) {
            log.warn("AddBlock: bad_prev_global_hash (login={}, blockchainName={}, globalNumber={}, clientPrev='{}', serverPrev='{}')",
                    login, blockchainName, globalNumber, nn(prevGlobalHashHex), nn(st.getLastGlobalHash()));
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_prev_global_hash", serverLastNum, serverLastHash);
        }

        // ===========================
        // ЛИНИИ (строго)
        // ===========================

        int li = block.lineIndex;
        int ln = block.lineNumber;

        if (globalNumber == 0) {
            // genesis
            if (li != 0 || ln != 0) {
                log.warn("AddBlock: bad_genesis_line_fields (login={}, blockchainName={}, lineIndex={}, lineNumber={})",
                        login, blockchainName, li, ln);
                return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_genesis_line_fields", serverLastNum, serverLastHash);
            }
        } else {
            // MVP: запрещаем lineIndex=0 для не-genesis (чтобы техблоки не пролезли случайно)
            if (li == 0) {
                log.warn("AddBlock: line0_only_genesis (login={}, blockchainName={}, globalNumber={}, lineIndex={})",
                        login, blockchainName, globalNumber, li);
                return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "line0_only_genesis", serverLastNum, serverLastHash);
            }
            if (li < 1 || li > 7) {
                log.warn("AddBlock: bad_line_index (login={}, blockchainName={}, globalNumber={}, lineIndex={})",
                        login, blockchainName, globalNumber, li);
                return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_line_index", serverLastNum, serverLastHash);
            }

            int expectedLineNumber = st.getLastLineNumber(li) + 1;
            if (ln != expectedLineNumber) {
                log.warn("AddBlock: bad_line_number (login={}, blockchainName={}, globalNumber={}, lineIndex={}, пришёлLineNumber={}, ожидалиLineNumber={}, lastLineNumber={})",
                        login, blockchainName, globalNumber, li, ln, expectedLineNumber, st.getLastLineNumber(li));
                return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_line_number", serverLastNum, serverLastHash);
            }
        }

        // prevLineHash берём из state по lineIndex:
        //  - genesis: 32 нулей
        //  - иначе: st.getLastLineHash(li) (для первой записи в линии это будет hash genesis)
        final byte[] prevLineHash32;
        final String prevLineHashHex;
        try {
            prevLineHashHex = computePrevLineHashHex(st, li);
            prevLineHash32 = hexTo32(prevLineHashHex);
        } catch (Exception e) {
            log.warn("AddBlock: bad_prev_line_hash_in_state (login={}, blockchainName={}, globalNumber={}, lineIndex={})",
                    login, blockchainName, globalNumber, li, e);
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "bad_prev_line_hash_in_state", serverLastNum, serverLastHash);
        }

        // crypto verify
        boolean ok = BchCryptoVerifier.verifyAll(
                login,
                prevGlobalHash32,
                prevLineHash32,
                block.getRawBytes(),
                block.getSignature64(),
                loginKey32,
                block.getHash32()
        );

        if (!ok) {
            log.warn("AddBlock: bad_signature_or_hash (login={}, blockchainName={}, globalNumber={}, lineIndex={}, lineNumber={})",
                    login, blockchainName, globalNumber, li, ln);
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_signature_or_hash", serverLastNum, serverLastHash);
        }

        String newHashHex = toHex(block.getHash32());

        // write
        try {
            dbWriter.appendBlockAndState(
                    login,
                    blockchainName,
                    nn(prevGlobalHashHex),
                    prevLineHashHex,
                    block,
                    st,
                    newHashHex
            );
        } catch (Exception e) {
            log.error("AddBlock: внутренняя ошибка при записи блока (login={}, blockchainName={}, globalNumber={}, newHash={})",
                    login, blockchainName, globalNumber, newHashHex, e);
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "internal_error", serverLastNum, serverLastHash);
        }

        log.info("✅ AddBlock ok: login={}, blockchainName={}, globalNumber={}, lineIndex={}, lineNumber={}, newHash={}",
                login, blockchainName, globalNumber, li, ln, newHashHex);

        return new AddBlockResult(WireCodes.Status.OK, null, globalNumber, newHashHex);
    }

    /**
     * ✅ Правило:
     *  - lineIndex=0 (genesis линия): prevLineHash = 32 нулей (пустая строка => hexTo32 даст 32 нуля)
     *  - lineIndex>0:
     *      - если в этой линии ещё нет блоков (lastLineNumber==0) => prevLineHash = hash(genesis) (line0 hash)
     *      - иначе => prevLineHash = lastLineHash(lineIndex)
     */
    private static String computePrevLineHashHex(BlockchainStateEntry st, int lineIndex) {
        if (lineIndex == 0) {
            return ""; // -> 32 нуля
        }

        int lastLn = st.getLastLineNumber(lineIndex);
        if (lastLn == 0) {
            // первая запись линии -> от genesis
            String genesis = nn(st.getLastLineHash(0));
            if (!genesis.isBlank()) return genesis;

            // fallback: если line0 почему-то не заполнена, но genesis глобально есть
            String g = nn(st.getLastGlobalHash());
            if (!g.isBlank()) return g;

            return "";
        }

        return nn(st.getLastLineHash(lineIndex));
    }

    private static final class AddBlockResult {
        final int httpStatus;
        final String reasonCode;
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

    private static String safeBodyType(BchBlockEntry b) {
        try { return String.valueOf(b.body.type()); } catch (Exception e) { return "unknown"; }
    }

    private static String safeBodyVersion(BchBlockEntry b) {
        try { return String.valueOf(b.body.version()); } catch (Exception e) { return "unknown"; }
    }

    private static long safeAdd(long x, long y) {
        long r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new IllegalArgumentException("overflow: " + x + " + " + y);
        }
        return r;
    }
}