package test.it.ws;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import blockchain.body.HeaderBody;
import blockchain.body.TextBody;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import test.it.utils.TestConfig;
import utils.crypto.Ed25519Util;

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

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .buildAsync(URI.create(TestConfig.WS_URI), new WebSocket.Listener() {

                    private int step = 0;

                    private String lastGlobalHashHex = ZERO64;
                    private String lastLineHashHex   = ZERO64;

                    @Override
                    public void onOpen(WebSocket ws) {
                        System.out.println("✅ WS connected: " + TestConfig.WS_URI);
                        ws.request(1);

                        // 1) HEADER block: global=0, line=0, lineNumber=0
                        byte[] headerFull = buildHeaderBlockFullBytes(
                                0,
                                (short) 0,
                                0,
                                ZERO32,
                                ZERO32
                        );

                        String json = buildAddBlockJson(
                                "test-add-header",
                                TestConfig.TEST_LOGIN,
                                TestConfig.TEST_BCH_NAME,
                                0,
                                ZERO64,
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

                                String serverLastGlobalHash = extractPayloadString(msg, "serverLastGlobalHash");
                                String serverLastLineHash   = extractPayloadString(msg, "serverLastLineHash");

                                if (serverLastGlobalHash == null || serverLastGlobalHash.isBlank()) {
                                    System.out.println("❌ No serverLastGlobalHash in response");
                                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "bad-response");
                                    return CompletableFuture.completedFuture(null);
                                }
                                if (serverLastLineHash == null || serverLastLineHash.isBlank()) {
                                    serverLastLineHash = serverLastGlobalHash;
                                }

                                lastGlobalHashHex = serverLastGlobalHash;
                                lastLineHashHex   = serverLastLineHash;

                                byte[] prevGlobal32 = hexToBytes32(lastGlobalHashHex);
                                byte[] prevLine32   = hexToBytes32(lastLineHashHex);

                                // 2) TEXT block: global=1, line=0, lineNumber=1
                                byte[] textFull = buildTextBlockFullBytes(
                                        1,
                                        (short) 0,
                                        1,
                                        prevGlobal32,
                                        prevLine32,
                                        "Hello from test client"
                                );

                                String json2 = buildAddBlockJson(
                                        "test-add-text",
                                        TestConfig.TEST_LOGIN,
                                        TestConfig.TEST_BCH_NAME,
                                        1,
                                        lastGlobalHashHex,
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

        // В твоём текущем коде HeaderBody формата type=0 ver=1:
        // [type][ver][tag "SHiNE001"][loginLen][login]
        HeaderBody body = new HeaderBody(TestConfig.TEST_LOGIN);
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

        // recordSize = только RAW = header + body
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

        byte[] preimage = BchCryptoVerifier.buildPreimage(
                TestConfig.TEST_LOGIN,
                prevGlobalHash32,
                prevLineHash32,
                rawBytes
        );

        byte[] hash32 = BchCryptoVerifier.sha256(preimage);
        byte[] signature64 = Ed25519Util.sign(hash32, TestConfig.LOGIN_PRIV_KEY);

        return new BchBlockEntry(
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
                                            String login,
                                            String blockchainName,
                                            int globalNumber,
                                            String prevGlobalHashHex,
                                            String blockBytesB64) {
        return """
            {
              "op": "AddBlock",
              "requestId": "%s",
              "payload": {
                "login": "%s",
                "blockchainName": "%s",
                "globalNumber": %d,
                "prevGlobalHash": "%s",
                "blockBytesB64": "%s"
              }
            }
            """.formatted(requestId, login, blockchainName, globalNumber, prevGlobalHashHex, blockBytesB64);
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
