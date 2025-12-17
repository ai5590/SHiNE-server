package Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import utils.crypto.Ed25519Util;
import blockchain.body.HeaderBody;
import blockchain.body.TextBody;
import blockchain_new.BchCryptoVerifier_new;
import blockchain_new.BchBlockEntry_new;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class Test_AddBlock_new_NoAuth {

    private static final String WS_URI = "ws://localhost:7070/ws";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String TEST_LOGIN = "anya24";
    private static final long   TEST_BCH_ID = 4222L;

    private static final byte[] LOGIN_PRIV_KEY;
    private static final byte[] LOGIN_PUB_KEY;

    static {
        LOGIN_PRIV_KEY = Ed25519Util.generatePrivateKeyFromString("test-ed25519-login-11" + TEST_LOGIN);
        LOGIN_PUB_KEY  = Ed25519Util.derivePublicKey(LOGIN_PRIV_KEY);
    }

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new WebSocket.Listener() {

                    private int step = 0;

                    // Эти значения обновим ПО ОТВЕТУ сервера на header
                    private String lastGlobalHashHex = ZERO64;
                    private String lastLineHashHex   = ZERO64;

                    @Override
                    public void onOpen(WebSocket ws) {
                        System.out.println("✅ WS connected: " + WS_URI);
                        ws.request(1);

                        // 1) HEADER (global=0, line=0, lineNumber=0)
                        byte[] headerFull = buildHeaderBlockFullBytes(
                                /*global*/0,
                                /*lineIndex*/(short)0,
                                /*lineBlock*/0,
                                /*prevGlobal*/ZERO32,
                                /*prevLine*/ZERO32
                        );

                        String json = buildAddBlockJson(
                                "test-add-header",
                                TEST_BCH_ID,
                                0,
                                ZERO64,                 // prevGlobalHash для первого блока — нули
                                base64(headerFull)
                        );

                        System.out.println("\n📤 SEND #1 (HEADER):\n" + json);
                        ws.sendText(json, true);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        String msg = data.toString();
                        System.out.println("\n📥 RECV:\n" + msg);
                        System.out.println("-----------------------------------------------------");

                        try {
                            int status = extractStatus(msg);

                            if (step == 0) {
                                if (status != 200) {
                                    System.out.println("❌ HEADER rejected, status=" + status);
                                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "fail");
                                    return CompletableFuture.completedFuture(null);
                                }

                                // Берём ИМЕННО ТОТ хэш, который сервер сохранил в state
                                String serverLastGlobalHash = extractPayloadString(msg, "serverLastGlobalHash");
                                String serverLastLineHash   = extractPayloadString(msg, "serverLastLineHash");

                                if (serverLastGlobalHash == null || serverLastGlobalHash.isBlank()) {
                                    System.out.println("❌ No serverLastGlobalHash in response");
                                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "bad-response");
                                    return CompletableFuture.completedFuture(null);
                                }
                                if (serverLastLineHash == null || serverLastLineHash.isBlank()) {
                                    // fallback: пусть будет как global (если сервер так хранит)
                                    serverLastLineHash = serverLastGlobalHash;
                                }

                                lastGlobalHashHex = serverLastGlobalHash;
                                lastLineHashHex   = serverLastLineHash;

                                byte[] prevGlobal32 = hexToBytes32(lastGlobalHashHex);
                                byte[] prevLine32   = hexToBytes32(lastLineHashHex);

                                // 2) TEXT (global=1, line=0, lineNumber=1)
                                byte[] textFull = buildTextBlockFullBytes(
                                        /*global*/1,
                                        /*lineIndex*/(short)0,
                                        /*lineBlock*/1,
                                        prevGlobal32,
                                        prevLine32,
                                        "Hello from test client"
                                );

                                String json2 = buildAddBlockJson(
                                        "test-add-text",
                                        TEST_BCH_ID,
                                        1,
                                        lastGlobalHashHex,     // prevGlobalHash = хэш header'а из ответа сервера
                                        base64(textFull)
                                );

                                System.out.println("\n📤 SEND #2 (TEXT):\n" + json2);
                                step = 1;
                                ws.sendText(json2, true);

                            } else if (step == 1) {
                                if (status != 200) {
                                    System.out.println("❌ TEXT rejected, status=" + status);
                                } else {
                                    System.out.println("✅ Done. Closing.");
                                }
                                ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
                            }

                        } catch (Exception e) {
                            e.printStackTrace(System.out);
                            ws.sendClose(WebSocket.NORMAL_CLOSURE, "exception");
                        }

                        ws.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        System.out.println("❌ WS error: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        System.out.println("🔚 WS closed. code=" + statusCode + " reason=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
    }

    // =================================================================================
    //                                   BUILD BLOCKS
    // =================================================================================

    private static byte[] buildHeaderBlockFullBytes(int globalNumber,
                                                    short lineIndex,
                                                    int lineBlockNumber,
                                                    byte[] prevGlobalHash32,
                                                    byte[] prevLineHash32) {

        HeaderBody body = new HeaderBody(
                TEST_BCH_ID,
                TEST_LOGIN,
                0, 0,
                (short) 1,
                0L,
                LOGIN_PUB_KEY
        );
        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static byte[] buildTextBlockFullBytes(int globalNumber,
                                                  short lineIndex,
                                                  int lineBlockNumber,
                                                  byte[] prevGlobalHash32,
                                                  byte[] prevLineHash32,
                                                  String text) {

        TextBody body = new TextBody(text);
        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static byte[] buildSignedBlockFullBytes(int globalNumber,
                                                    short lineIndex,
                                                    int lineBlockNumber,
                                                    byte[] bodyBytes,
                                                    byte[] prevGlobalHash32,
                                                    byte[] prevLineHash32) {

        long ts = System.currentTimeMillis() / 1000L;

        int recordSize =
                BchBlockEntry_new.RAW_HEADER_SIZE +
                        bodyBytes.length +
                        BchBlockEntry_new.SIGNATURE_LEN +
                        BchBlockEntry_new.HASH_LEN;

        byte[] rawBytes = ByteBuffer.allocate(BchBlockEntry_new.RAW_HEADER_SIZE + bodyBytes.length)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(recordSize)
                .putInt(globalNumber)
                .putLong(ts)
                .putShort(lineIndex)
                .putInt(lineBlockNumber)
                .put(bodyBytes)
                .array();

        byte[] preimage = BchCryptoVerifier_new.buildPreimage(
                TEST_LOGIN,
                prevGlobalHash32,
                prevLineHash32,
                rawBytes
        );

        byte[] hash32 = BchCryptoVerifier_new.sha256(preimage);

        // если у тебя подпись должна быть по preimage — меняй тут
        byte[] signature64 = Ed25519Util.sign(hash32, LOGIN_PRIV_KEY);

        return new BchBlockEntry_new(
                globalNumber,
                ts,
                lineIndex,
                lineBlockNumber,
                bodyBytes,
                signature64,
                hash32
        ).toBytes();
    }

    // =================================================================================
    //                                    JSON BUILD
    // =================================================================================

    private static String buildAddBlockJson(String requestId,
                                            long blockchainId,
                                            int globalNumber,
                                            String prevGlobalHashHex,
                                            String blockBytesB64) {
        return """
            {
              "op": "AddBlock",
              "requestId": "%s",
              "payload": {
                "login": "%s",
                "blockchainId": %d,
                "globalNumber": %d,
                "prevGlobalHash": "%s",
                "blockBytesB64": "%s"
              }
            }
            """.formatted(requestId, TEST_LOGIN, blockchainId, globalNumber, prevGlobalHashHex, blockBytesB64);
    }

    // =================================================================================
    //                                    HELPERS
    // =================================================================================

    private static int extractStatus(String json) {
        try {
            JsonNode root = JSON.readTree(json);
            if (root.has("status")) return root.get("status").asInt();
        } catch (Exception ignore) {}
        return -1;
    }

    private static String extractPayloadString(String json, String field) {
        try {
            JsonNode root = JSON.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has(field)) {
                return payload.get(field).asText();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

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
}
