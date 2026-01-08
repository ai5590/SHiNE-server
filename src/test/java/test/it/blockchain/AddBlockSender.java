package test.it.blockchain;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import blockchain.body.BodyRecord;
import test.it.utils.json.JsonParsers;
import test.it.utils.TestConfig;
import test.it.utils.TestIds;
import test.it.utils.log.TestLog;
import test.it.utils.ws.WsSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AddBlockSender — отправка AddBlock поверх одного WsSession:
 *  - берёт номера/prev-hash из ChainState
 *  - строит raw/hash/signature
 *  - отправляет AddBlock
 *  - проверяет serverLastGlobalHash == localHash
 *  - обновляет ChainState
 */
public final class AddBlockSender {

    private static final byte[] ZERO32 = new byte[32];
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

        short lineIndex = body.expectedLineIndex();

        if (lineIndex == 0) {
            if (state.globalLastNumber() != -1) throw new IllegalStateException("HEADER должен быть первым: globalLastNumber уже " + state.globalLastNumber());
        } else {
            if (!state.hasHeader()) throw new IllegalStateException("Нельзя слать line=" + lineIndex + " до HEADER (нет headerHash32)");
        }

        int globalNumber = state.nextGlobalNumber();
        int lineNumber = state.nextLineNumber(lineIndex);

        byte[] prevGlobalHash32 = (lineIndex == 0) ? ZERO32 : state.prevGlobalHash32ForNext(lineIndex);
        byte[] prevLineHash32   = (lineIndex == 0) ? ZERO32 : state.prevLineHash32ForNext(lineIndex);

        long ts = System.currentTimeMillis() / 1000L;
        byte[] bodyBytes = body.toBytes();

        int recordSize = BchBlockEntry.RAW_HEADER_SIZE + bodyBytes.length;

        byte[] rawBytes = ByteBuffer.allocate(recordSize)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(recordSize)
                .putInt(globalNumber)
                .putLong(ts)
                .putShort(lineIndex)
                .putInt(lineNumber)
                .put(bodyBytes)
                .array();

        byte[] preimage = BchCryptoVerifier.buildPreimage(login, prevGlobalHash32, prevLineHash32, rawBytes);
        byte[] hash32 = BchCryptoVerifier.sha256(preimage);
        byte[] signature64 = utils.crypto.Ed25519Util.sign(hash32, loginPrivKey);

        BchBlockEntry entry = new BchBlockEntry(globalNumber, ts, lineIndex, lineNumber, bodyBytes, signature64, hash32);

        String prevGlobalHashHex = (globalNumber == 0) ? ZERO64 : state.globalLastHashHex();

        String reqJson = buildAddBlockJson(blockchainName, globalNumber, prevGlobalHashHex, base64(entry.toBytes()));
        String op = "AddBlock(user=" + login + ", global=" + globalNumber + ", line=" + lineIndex + ", lineNum=" + lineNumber + ")";

        String resp = ws.call(op, reqJson, timeout);

        assert200(op, resp);

        String serverLastGlobalHash = JsonMini.extractPayloadString(resp, "serverLastGlobalHash");
        assertNotNull(serverLastGlobalHash, op + ": payload.serverLastGlobalHash must not be null");
        assertEquals(64, serverLastGlobalHash.trim().length(), op + ": serverLastGlobalHash must be 64 hex chars");

        String localHashHex = bytesToHex64(hash32);

        if (TestConfig.DEBUG()) {
            TestLog.info(op + ": localHash=" + localHashHex);
            TestLog.info(op + ": serverLastGlobalHash=" + serverLastGlobalHash);
        }

        assertEquals(localHashHex, serverLastGlobalHash, op + ": serverLastGlobalHash must match local hash");

        state.applyAppendedBlock(globalNumber, lineIndex, lineNumber, hash32);

        if (TestConfig.DEBUG()) TestLog.info(op + ": state updated");
    }

    private static String buildAddBlockJson(String blockchainName, int globalNumber, String prevGlobalHashHex, String blockBytesB64) {
        String requestId = TestIds.next("addblock");
        return """
            {
              "op": "AddBlock",
              "requestId": "%s",
              "payload": {
                "blockchainName": "%s",
                "globalNumber": %d,
                "prevGlobalHash": "%s",
                "blockBytesB64": "%s"
              }
            }
            """.formatted(requestId, blockchainName, globalNumber, prevGlobalHashHex, blockBytesB64);
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
}