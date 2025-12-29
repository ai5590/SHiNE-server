package test.it.ws;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import blockchain.body.HeaderBody;
import blockchain.body.ReactionBody;
import blockchain.body.TextBody;
import test.it.utils.JsonParsers;
import test.it.utils.TestConfig;
import utils.crypto.Ed25519Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AddBlockFlow
 *
 * Держит локальное состояние цепочки:
 *  - last globalNumber / last globalHash
 *  - last lineNum / last lineHash для каждой линии
 *
 * И умеет:
 *  - собрать следующий блок (HEADER / TEXT / REACTION)
 *  - отправить AddBlock в сервер (через WsJsonOneShot)
 *  - проверить serverLastGlobalHash == localHash
 *  - обновить локальное состояние
 *
 * Важно:
 *  - Этот класс НЕ занимается красивыми логами. Только логика + проверки.
 */
public final class AddBlockFlow {

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    // линии как у тебя
    public static final short LINE_HEADER = 0;
    public static final short LINE_TEXT   = 1;
    public static final short LINE_REACT  = 2;

    // локальное состояние
    private final int[] lineLastNumber = new int[8];
    private final String[] lineLastHashHex = new String[8];

    private int globalLastNumber = -1;
    private String globalLastHashHex = ZERO64;

    private byte[] headerHash32 = null;

    public AddBlockFlow() {
        for (int i = 0; i < 8; i++) lineLastHashHex[i] = "";
    }

    // =================================================================================
    // PUBLIC API
    // =================================================================================

    /** Шлём HEADER (global=0, line=0, lineNum=0). Должно быть ПЕРВЫМ. */
    public void sendHeader0(Duration timeout) {
        assertEquals(-1, globalLastNumber, "HEADER должен идти первым: globalLastNumber сейчас уже " + globalLastNumber);

        BuiltBlock header = buildHeaderBlock(
                0,
                LINE_HEADER,
                0,
                ZERO32,
                ZERO32
        );

        String req = buildAddBlockJson(TestConfig.BCH_NAME(), 0, ZERO64, base64(header.fullBytes));
        String resp = WsJsonOneShot.request(req, timeout);

        assert200("AddBlock(HEADER)", resp);

        String serverLastGlobalHash0 = extractPayloadString(resp, "serverLastGlobalHash");
        assertNotNull(serverLastGlobalHash0, "HEADER: payload.serverLastGlobalHash must not be null");
        assertEquals(64, serverLastGlobalHash0.trim().length(), "HEADER: serverLastGlobalHash must be 64 hex chars");

        String localHash0 = bytesToHex64(header.hash32);
        assertEquals(localHash0, serverLastGlobalHash0, "HEADER: serverLastGlobalHash должен совпасть с локальным hash");

        // обновляем локальное состояние
        headerHash32 = header.hash32;
        globalLastNumber = 0;
        globalLastHashHex = localHash0;

        lineLastNumber[LINE_HEADER] = 0;
        lineLastHashHex[LINE_HEADER] = localHash0;
    }

    /** Шлём следующий TEXT блок в line=1. */
    public BuiltBlock sendNextText(String text, Duration timeout) {
        assertNotNull(headerHash32, "TEXT нельзя слать до HEADER (headerHash32 == null)");

        int nextGlobal = globalLastNumber + 1;
        int lineNum = nextLineNum(LINE_TEXT);
        byte[] prevLineHash = prevLineHash32(LINE_TEXT);

        BuiltBlock b = buildTextBlock(
                nextGlobal,
                LINE_TEXT,
                lineNum,
                hexToBytes32(globalLastHashHex),
                prevLineHash,
                text
        );

        String req = buildAddBlockJson(TestConfig.BCH_NAME(), nextGlobal, globalLastHashHex, base64(b.fullBytes));
        String resp = WsJsonOneShot.request(req, timeout);

        assert200("AddBlock(TEXT)", resp);

        String serverLastGlobalHash = extractPayloadString(resp, "serverLastGlobalHash");
        assertNotNull(serverLastGlobalHash, "TEXT: payload.serverLastGlobalHash must not be null");
        assertEquals(64, serverLastGlobalHash.trim().length(), "TEXT: serverLastGlobalHash must be 64 hex chars");

        String localHash = bytesToHex64(b.hash32);
        assertEquals(localHash, serverLastGlobalHash, "TEXT: serverLastGlobalHash должен совпасть с локальным hash");

        // обновляем состояние
        globalLastNumber = nextGlobal;
        globalLastHashHex = localHash;
        lineLastNumber[LINE_TEXT] = lineNum;
        lineLastHashHex[LINE_TEXT] = localHash;

        return b;
    }

    /** Шлём следующий REACTION блок в line=2, ссылаясь на конкретный блок. */
    public BuiltBlock sendNextReaction(int reactionCode,
                                       String toBlockchainName,
                                       int toBlockGlobalNumber,
                                       byte[] toBlockHash32,
                                       Duration timeout) {
        assertNotNull(headerHash32, "REACTION нельзя слать до HEADER (headerHash32 == null)");
        assertNotNull(toBlockHash32, "toBlockHash32 is null");
        assertEquals(32, toBlockHash32.length, "toBlockHash32 must be 32 bytes");

        int nextGlobal = globalLastNumber + 1;
        int lineNum = nextLineNum(LINE_REACT);
        byte[] prevLineHash = prevLineHash32(LINE_REACT);

        BuiltBlock b = buildReactionBlock(
                nextGlobal,
                LINE_REACT,
                lineNum,
                hexToBytes32(globalLastHashHex),
                prevLineHash,
                reactionCode,
                toBlockchainName,
                toBlockGlobalNumber,
                toBlockHash32
        );

        String req = buildAddBlockJson(TestConfig.BCH_NAME(), nextGlobal, globalLastHashHex, base64(b.fullBytes));
        String resp = WsJsonOneShot.request(req, timeout);

        assert200("AddBlock(REACT)", resp);

        String serverLastGlobalHash = extractPayloadString(resp, "serverLastGlobalHash");
        assertNotNull(serverLastGlobalHash, "REACT: payload.serverLastGlobalHash must not be null");
        assertEquals(64, serverLastGlobalHash.trim().length(), "REACT: serverLastGlobalHash must be 64 hex chars");

        String localHash = bytesToHex64(b.hash32);
        assertEquals(localHash, serverLastGlobalHash, "REACT: serverLastGlobalHash должен совпасть с локальным hash");

        // обновляем состояние
        globalLastNumber = nextGlobal;
        globalLastHashHex = localHash;
        lineLastNumber[LINE_REACT] = lineNum;
        lineLastHashHex[LINE_REACT] = localHash;

        return b;
    }

    // getters для итогов/логов (если надо)
    public int globalLastNumber() { return globalLastNumber; }
    public String globalLastHashHex() { return globalLastHashHex; }
    public int lineLastNumber(short line) { return lineLastNumber[line]; }
    public String lineLastHashHex(short line) { return lineLastHashHex[line]; }

    // =================================================================================
    // INTERNALS: line helpers
    // =================================================================================

    /** Следующий lineNum: если в линии было N блоков, новый будет N+1 (для line>0). Для line0 здесь не используется. */
    private int nextLineNum(short lineIndex) {
        if (lineIndex < 0 || lineIndex > 7) throw new IllegalArgumentException("lineIndex must be 0..7");
        if (lineIndex == 0) return 0;
        return lineLastNumber[lineIndex] + 1;
    }

    /**
     * prevLineHash32 по твоему правилу:
     *  - для первого блока линии (lineLastNumber[line]==0): prevLineHash = hash(нулевого блока)
     *  - иначе: prevLineHash = hash последнего блока этой линии
     *
     * Важно: для line0 здесь не используем (header имеет prevLine=ZERO32).
     */
    private byte[] prevLineHash32(short lineIndex) {
        if (lineIndex < 0 || lineIndex > 7) throw new IllegalArgumentException("lineIndex must be 0..7");
        if (lineIndex == 0) return ZERO32;

        if (lineLastNumber[lineIndex] == 0) {
            // первый блок линии -> от нулевого блока
            if (headerHash32 == null || headerHash32.length != 32) {
                throw new IllegalStateException("headerHash32 is not set but required for first block of line " + lineIndex);
            }
            return headerHash32;
        }

        String lastHex = lineLastHashHex[lineIndex];
        if (lastHex == null || lastHex.isBlank()) {
            throw new IllegalStateException("lineLastHashHex[" + lineIndex + "] is blank but lineLastNumber>0");
        }
        return hexToBytes32(lastHex);
    }

    // =================================================================================
    // INTERNALS: build blocks
    // =================================================================================

    /** Небольшой холдер, чтобы flow мог использовать hash32 как prevGlobal/prevLine и как toBlockHash. */
    public static final class BuiltBlock {
        public final byte[] fullBytes;
        public final byte[] hash32;

        public BuiltBlock(byte[] fullBytes, byte[] hash32) {
            this.fullBytes = fullBytes;
            this.hash32 = hash32;
        }
    }

    private static BuiltBlock buildHeaderBlock(int globalNumber,
                                               short lineIndex,
                                               int lineBlockNumber,
                                               byte[] prevGlobalHash32,
                                               byte[] prevLineHash32) {

        HeaderBody body = new HeaderBody(TestConfig.LOGIN());
        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static BuiltBlock buildTextBlock(int globalNumber,
                                             short lineIndex,
                                             int lineBlockNumber,
                                             byte[] prevGlobalHash32,
                                             byte[] prevLineHash32,
                                             String text) {

        TextBody body = new TextBody(text);
        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static BuiltBlock buildReactionBlock(int globalNumber,
                                                 short lineIndex,
                                                 int lineBlockNumber,
                                                 byte[] prevGlobalHash32,
                                                 byte[] prevLineHash32,
                                                 int reactionCode,
                                                 String toBlockchainName,
                                                 int toBlockGlobalNumber,
                                                 byte[] toBlockHash32) {

        ReactionBody body = new ReactionBody(
                reactionCode,
                toBlockchainName,
                toBlockGlobalNumber,
                toBlockHash32 // [32] сырые 32 байта, как ты утвердил
        );

        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static BuiltBlock buildSignedBlockFullBytes(int globalNumber,
                                                        short lineIndex,
                                                        int lineBlockNumber,
                                                        byte[] bodyBytes,
                                                        byte[] prevGlobalHash32,
                                                        byte[] prevLineHash32) {

        long ts = System.currentTimeMillis() / 1000L;

        int recordSize = BchBlockEntry.RAW_HEADER_SIZE + bodyBytes.length;

        byte[] rawBytes = ByteBuffer.allocate(recordSize)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(recordSize)
                .putInt(globalNumber)
                .putLong(ts)
                .putShort(lineIndex)
                .putInt(lineBlockNumber)
                .put(bodyBytes)
                .array();

        // Ключевой момент: preimage должен совпасть с серверным правилом.
        // Сервер НЕ получает prevLineHash по сети — он берёт его из своего состояния линии.
        // Поэтому в тесте мы обязаны передавать сюда ровно тот же prevLineHash32.
        byte[] preimage = BchCryptoVerifier.buildPreimage(
                TestConfig.LOGIN(),
                prevGlobalHash32,
                prevLineHash32,
                rawBytes
        );

        byte[] hash32 = BchCryptoVerifier.sha256(preimage);

        byte[] signature64 = Ed25519Util.sign(hash32, TestConfig.LOGIN_PRIV_KEY());

        byte[] full = new BchBlockEntry(
                globalNumber,
                ts,
                lineIndex,
                lineBlockNumber,
                bodyBytes,
                signature64,
                hash32
        ).toBytes();

        return new BuiltBlock(full, hash32);
    }

    // =================================================================================
    // INTERNALS: json helpers
    // =================================================================================

    private static String buildAddBlockJson(String blockchainName,
                                           int globalNumber,
                                           String prevGlobalHashHex,
                                           String blockBytesB64) {
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
            """.formatted(WsJsonOneShot.FIXED_REQUEST_ID, blockchainName, globalNumber, prevGlobalHashHex, blockBytesB64);
    }

    private static void assert200(String op, String resp) {
        int st = JsonParsers.status(resp);
        assertEquals(200, st, op + ": expected status=200, but got=" + st + ", resp=" + resp);
    }

    private static String extractPayloadString(String json, String field) {
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            com.fasterxml.jackson.databind.JsonNode payload = root.get("payload");
            if (payload != null && payload.has(field)) {
                return payload.get(field).asText();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    // =================================================================================
    // INTERNALS: hex helpers
    // =================================================================================

    private static byte[] hexToBytes32(String hex) {
        if (hex == null) throw new IllegalArgumentException("hex is null");
        String s = hex.trim();
        if (s.length() != 64) throw new IllegalArgumentException("hex must be 64 chars, got " + s.length());
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad hex at pos " + (i * 2));
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String bytesToHex64(byte[] b32) {
        if (b32 == null || b32.length != 32) throw new IllegalArgumentException("b32 must be 32 bytes");
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