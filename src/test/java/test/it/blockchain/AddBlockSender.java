package test.it.blockchain;

import blockchain.BchBlockEntry;
import blockchain.body.*;
import test.it.utils.TestConfig;
import test.it.utils.TestIds;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestLog;
import test.it.utils.ws.WsSession;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AddBlockSender — под новый формат BchBlockEntry:
 *  - block хранит только preimage + signature
 *  - hash32 вычисляется как sha256(preimage)
 *  - signature = Ed25519.sign(hash32)
 */
public final class AddBlockSender {

    private static final String ZERO64 = "0".repeat(64);

    private final WsSession ws;
    private final ChainState state;

    private final String login;
    private final String blockchainName;
    private final byte[] loginPrivKey;

    public AddBlockSender(WsSession ws, ChainState state, String login, String blockchainName, byte[] loginPrivKey) {
        this.ws = ws;
        this.state = state;
        this.login = login;
        this.blockchainName = blockchainName;
        this.loginPrivKey = (loginPrivKey == null ? null : loginPrivKey.clone());
        if (this.ws == null) throw new IllegalArgumentException("ws == null");
        if (this.loginPrivKey == null) throw new IllegalArgumentException("loginPrivKey == null");
    }

    public ChainState state() { return state; }

    public void send(BodyRecord body, Duration timeout) {
        if (body == null) throw new IllegalArgumentException("body == null");

        body.check();

        boolean isHeader = (body instanceof HeaderBody);

        if (isHeader) {
            if (state.lastBlockNumber() != -1) {
                throw new IllegalStateException("HEADER должен быть первым: lastBlockNumber уже " + state.lastBlockNumber());
            }
        } else {
            if (!state.hasHeader()) {
                throw new IllegalStateException("Нельзя слать блоки до HEADER (нет headerHash32)");
            }
        }

        int blockNumber = state.nextBlockNumber();
        byte[] prevHash32 = state.prevHash32ForNext();
        long tsSec = System.currentTimeMillis() / 1000L;

        short type = typeOf(body);
        short subType = subTypeOf(body);
        short version = versionOf(body);

        byte[] bodyBytes = body.toBytes();

        // preimage -> hash32 -> signature
        byte[] preimage = buildPreimage(prevHash32, blockNumber, tsSec, type, subType, version, bodyBytes);
        byte[] hash32 = blockchain.BchCryptoVerifier.sha256(preimage);
        byte[] signature64 = utils.crypto.Ed25519Util.sign(hash32, loginPrivKey);

        BchBlockEntry entry = new BchBlockEntry(
                prevHash32,
                blockNumber,
                tsSec,
                type,
                subType,
                version,
                bodyBytes,
                signature64
        );

        String prevHashHexForReq = (blockNumber == 0) ? ZERO64 : state.lastBlockHashHex();

        String reqJson = buildAddBlockJson(blockchainName, blockNumber, prevHashHexForReq, base64(entry.toBytes()));
        String op = "AddBlock(user=" + login + ", block=" + blockNumber + ", type=" + (type & 0xFFFF) + ", sub=" + (subType & 0xFFFF) + ")";

        String resp = ws.call(op, reqJson, timeout);

        assert200(op, resp);

        String serverLastHash = JsonMini.extractPayloadString(resp, "serverLastBlockHash");
        if (serverLastHash == null) {
            // на случай старого имени, но по твоей просьбе мы на это больше не опираемся
            serverLastHash = JsonMini.extractPayloadString(resp, "serverLastGlobalHash");
        }

        assertNotNull(serverLastHash, op + ": payload.serverLastBlockHash must not be null");
        assertEquals(64, serverLastHash.trim().length(), op + ": serverLastBlockHash must be 64 hex chars");

        String localHashHex = bytesToHex64(entry.getHash32());

        if (TestConfig.DEBUG()) {
            TestLog.info(op + ": localHash=" + localHashHex);
            TestLog.info(op + ": serverLastBlockHash=" + serverLastHash);
        }

        assertEquals(localHashHex, serverLastHash, op + ": serverLastBlockHash must match local hash");

        state.applyAppendedBlock(blockNumber, entry.getHash32(), isHeader, type);

        // если это line-body — обновим thisLineNumber в state (для nextLine())
        if (body instanceof BodyHasLine hl) {
            short lineIndex = lineIndexByType(type);
            if (lineIndex != -1) {
                state.applyThisLineNumber(lineIndex, hl.thisLineNumber());
            }
        }

        if (TestConfig.DEBUG()) TestLog.info(op + ": state updated");
    }

    // ---------- request JSON ----------

    private static String buildAddBlockJson(String blockchainName, int blockNumber, String prevBlockHashHex, String blockBytesB64) {
        String requestId = TestIds.next("addblock");
        return """
            {
              "op": "AddBlock",
              "requestId": "%s",
              "payload": {
                "blockchainName": "%s",
                "blockNumber": %d,
                "prevBlockHash": "%s",
                "blockBytesB64": "%s"
              }
            }
            """.formatted(requestId, blockchainName, blockNumber, prevBlockHashHex, blockBytesB64);
    }

    private static void assert200(String op, String resp) {
        int st = JsonParsers.status(resp);
        assertEquals(200, st, op + ": expected status=200, but got=" + st + ", resp=" + resp);
        TestLog.ok(op + ": status=200");
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String bytesToHex64(byte[] b32) {
        char[] out = new char[64];
        final char[] HEX = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 32; i++) {
            int v = b32[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    // ---------- header extraction from body ----------

    private static short typeOf(BodyRecord body) {
        if (body instanceof HeaderBody) return HeaderBody.TYPE;
        if (body instanceof TextBody) return TextBody.TYPE;
        if (body instanceof ReactionBody) return ReactionBody.TYPE;
        if (body instanceof ConnectionBody) return ConnectionBody.TYPE;
        if (body instanceof UserParamBody) return UserParamBody.TYPE;
        throw new IllegalArgumentException("Unknown body class: " + body.getClass());
    }

    private static short subTypeOf(BodyRecord body) {
        if (body instanceof HeaderBody hb) return hb.subType;
        if (body instanceof TextBody tb) return tb.subType;
        if (body instanceof ReactionBody rb) return rb.subType;
        if (body instanceof ConnectionBody cb) return cb.subType;
        if (body instanceof UserParamBody ub) return ub.subType;
        throw new IllegalArgumentException("Unknown body class: " + body.getClass());
    }

    private static short versionOf(BodyRecord body) {
        if (body instanceof HeaderBody hb) return hb.version;
        if (body instanceof TextBody tb) return tb.version;
        if (body instanceof ReactionBody rb) return rb.version;
        if (body instanceof ConnectionBody cb) return cb.version;
        if (body instanceof UserParamBody ub) return ub.version;
        throw new IllegalArgumentException("Unknown body class: " + body.getClass());
    }

    // ---------- preimage builder (строго по BchBlockEntry) ----------

    private static byte[] buildPreimage(byte[] prevHash32,
                                        int blockNumber,
                                        long tsSec,
                                        short type,
                                        short subType,
                                        short version,
                                        byte[] bodyBytes) {

        int blockSize = BchBlockEntry.RAW_HEADER_SIZE + (bodyBytes == null ? 0 : bodyBytes.length);

        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(blockSize).order(java.nio.ByteOrder.BIG_ENDIAN);

        bb.put(prevHash32);
        bb.putInt(blockSize);
        bb.putInt(blockNumber);
        bb.putLong(tsSec);
        bb.putShort(type);
        bb.putShort(subType);
        bb.putShort(version);
        if (bodyBytes != null) bb.put(bodyBytes);

        return bb.array();
    }

    private static short lineIndexByType(short type) {
        int t = type & 0xFFFF;
        return switch (t) {
            case 0 -> blockchain.LineIndex.HEADER;
            case 1 -> blockchain.LineIndex.TEXT;
            case 3 -> blockchain.LineIndex.CONNECTION;
            case 4 -> blockchain.LineIndex.USER_PARAM;
            default -> (short) -1;
        };
    }
}