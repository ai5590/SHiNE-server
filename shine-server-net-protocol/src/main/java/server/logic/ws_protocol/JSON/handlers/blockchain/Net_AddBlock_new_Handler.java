package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain.BchBlockEntry;
import blockchain.BodyRecordParser;
import blockchain.body.BodyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.entyties.Blockchain.Net_AddBlock_new_Request;
import server.logic.ws_protocol.JSON.entyties.Blockchain.Net_AddBlock_new_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.BlockchainStateDAO;
import shine.db.entities.BlockchainStateEntry;
import utils.crypto.BchCryptoVerifier;
import utils.files.FileStoreUtil;

import java.util.Base64;

public class Net_AddBlock_new_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_AddBlock_new_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {

        Net_AddBlock_new_Request req = (Net_AddBlock_new_Request) baseReq;

        // 0) базовые проверки
        if (req.getBlockchainId() <= 0) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_BLOCKCHAIN_ID", "blockchainId <= 0");
        }
        if (req.getGlobalNumber() < 0) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_GLOBAL_NUMBER", "globalNumber < 0");
        }
        if (req.getLineNumber() < 0 || req.getLineNumber() > 7) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_LINE_NUMBER", "lineNumber must be 0..7");
        }
        if (req.getLineBlockNumber() < 0) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_LINE_BLOCK_NUMBER", "lineBlockNumber < 0");
        }
        if (req.getBlockBase64() == null || req.getBlockBase64().isBlank()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "EMPTY_BLOCK", "blockBase64 is empty");
        }

        // 1) грузим состояние из БД
        BlockchainStateDAO dao = BlockchainStateDAO.getInstance();
        BlockchainStateEntry state = dao.getByBlockchainId(req.getBlockchainId());
        if (state == null) {
            // на MVP можно: запретить добавление, пока цепочка не создана отдельно
            // либо разрешить только genesis/header — как ты делал раньше
            return NetExceptionResponseFactory.error(req, WireCodes.Status.CHAIN_NOT_FOUND, "CHAIN_NOT_FOUND", "chain not found in DB");
        }

        // 2) быстрые проверки на “подходит ли блок”
        int expectedGlobal = state.getLastGlobalNumber() + 1;
        int expectedLine = state.getLastLineNumber(req.getLineNumber()) + 1;

        String dbPrevGlobalHash = nn(state.getLastGlobalHash());
        String dbPrevLineHash   = nn(state.getLastLineHash(req.getLineNumber()));

        if (req.getGlobalNumber() != expectedGlobal) {
            return outOfSeq(req, state, req.getLineNumber(), "OUT_OF_SEQUENCE_GLOBAL");
        }
        if (!eqHash(req.getPrevGlobalHash(), dbPrevGlobalHash)) {
            return outOfSeq(req, state, req.getLineNumber(), "GLOBAL_HASH_MISMATCH");
        }
        if (req.getLineBlockNumber() != expectedLine) {
            return outOfSeq(req, state, req.getLineNumber(), "OUT_OF_SEQUENCE_LINE");
        }
        if (!eqHash(req.getPrevLineHash(), dbPrevLineHash)) {
            return outOfSeq(req, state, req.getLineNumber(), "LINE_HASH_MISMATCH");
        }

        // 3) декодируем блок
        byte[] fullBlockBytes;
        try {
            fullBlockBytes = Base64.getUrlDecoder().decode(req.getBlockBase64());
        } catch (IllegalArgumentException e) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_BASE64", "blockBase64 decode failed");
        }

        // 4) парсим .bch
        BchBlockEntry block;
        try {
            block = new BchBlockEntry(fullBlockBytes);
        } catch (Exception e) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_BLOCK_FORMAT", "cannot parse BchBlockEntry");
        }

        // 5) ПОЛНАЯ валидация: подпись/хэш/тело
        // ⚠️ ниже я оставляю общий вызов verifyAll как у тебя раньше,
        // но теперь prevHash берём из БД, а publicKey — из state (или из solana_users).
        byte[] prevHashGlobal32 = hexToBytes32(dbPrevGlobalHash);

        boolean verified = BchCryptoVerifier.verifyAll(
                state.getUserLogin(),
                req.getBlockchainId(),
                prevHashGlobal32,
                block.rawBytes,
                block.getSignature64(),
                block.getHash32(),
                Base64.getDecoder().decode(state.getPublicKeyBase64())
        );

        if (!verified) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "UNVERIFIED", "signature/hash verification failed");
        }

        // Проверка тела блока
        BodyRecord body = BodyRecordParser.parse(block.recordType, block.recordTypeVersion, block.body).check();

        // 6) TODO: извлечь lineNumber/lineBlockNumber/prevLineHash из body (если они реально в теле есть)
        // и сверить с req + DB. Сейчас оставляю как “крючок”.
        // BlockLineMeta meta = BlockLineMetaExtractor.extract(body);
        // if (meta.lineNumber != req.getLineNumber()) ...
        // if (meta.lineBlockNumber != req.getLineBlockNumber()) ...
        // if (!eqHash(meta.prevLineHashHex, dbPrevLineHash)) ...

        // 7) запись в файл (фактическое хранение блоков)
        FileStoreUtil.getInstance().addDataToBlockchain(req.getBlockchainId(), fullBlockBytes);

        // 8) TODO: обновление состояния в БД (вместо BchInfoManager)
        // - state.sizeBytes += fullBlockBytes.length
        // - state.lastGlobalNumber = req.globalNumber
        // - state.lastGlobalHash = bytesToHex(block.getHash32())
        // - state.lineX_last_number/hash обновить по lineNumber
        // - state.updatedAtMs = now
        // dao.upsert(state);

        // 9) ответ OK
        Net_AddBlock_new_Response resp = new Net_AddBlock_new_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);

        // можно вернуть “новое” состояние, но на MVP вернём хотя бы серверные last’ы до апдейта/после апдейта
        resp.setServerLastGlobalNumber(req.getGlobalNumber());
        resp.setServerLastGlobalHash(bytesToHex(block.getHash32()));
        resp.setServerLastLineNumber(req.getLineBlockNumber());
        resp.setServerLastLineHash(resp.getServerLastGlobalHash());
        resp.setReasonCode(null);

        return resp;
    }

    private static Net_AddBlock_new_Response outOfSeq(Net_AddBlock_new_Request req, BlockchainStateEntry state, int line, String reason) {
        Net_AddBlock_new_Response resp = new Net_AddBlock_new_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OUT_OF_SEQUENCE); // или свой статус
        resp.setReasonCode(reason);

        resp.setServerLastGlobalNumber(state.getLastGlobalNumber());
        resp.setServerLastGlobalHash(nn(state.getLastGlobalHash()));

        resp.setServerLastLineNumber(state.getLastLineNumber(line));
        resp.setServerLastLineHash(nn(state.getLastLineHash(line)));

        return resp;
    }

    private static boolean eqHash(String a, String b) {
        return nn(a).equalsIgnoreCase(nn(b));
    }

    private static String nn(String s) { return s == null ? "" : s.trim(); }

    private static byte[] hexToBytes32(String hex) {
        hex = nn(hex);
        if (hex.isEmpty()) return new byte[32];
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        if (out.length == 32) return out;
        byte[] full = new byte[32];
        int copy = Math.min(out.length, 32);
        System.arraycopy(out, out.length - copy, full, 32 - copy, copy);
        return full;
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}