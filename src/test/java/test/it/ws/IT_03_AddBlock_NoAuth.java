package test.it.ws;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import blockchain.body.HeaderBody;
import blockchain.body.TextBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.it.utils.JsonBuilders;
import test.it.utils.JsonParsers;
import test.it.utils.TestConfig;
import test.it.utils.WsTestClient;
import utils.crypto.Ed25519Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_03_AddBlock_NoAuth
 *
 * Интеграционный тест добавления блоков в персональный блокчейн без отдельной авторизации,
 * в формате твоих IT-тестов (ANSI, шаги, WsTestClient).
 *
 * Сценарий:
 *  1) AddBlock: HEADER (global=0, prevGlobalHash=ZERO64) -> ожидаем 200
 *     - забираем payload.serverLastGlobalHash
 *  2) AddBlock: TEXT   (global=1, prevGlobalHash=serverLastGlobalHash) -> ожидаем 200
 *
 * Примечание:
 *  - lastLineHash пока равен lastGlobalHash.
 *  - подпись блока делаем ключом логина (loginPrivKey).
 */
public class IT_03_AddBlock_NoAuth {

    // ANSI цвета
    private static final String R   = "\u001B[0m";
    private static final String G   = "\u001B[32m";
    private static final String Y   = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String C   = "\u001B[36m";

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    private static void line() {
        System.out.println(C + "------------------------------------------------------------" + R);
    }

    private static void title(String s) {
        System.out.println(C + "\n============================================================" + R);
        System.out.println(C + s + R);
        System.out.println(C + "============================================================\n" + R);
    }

    private static void stepTitle(String s) {
        System.out.println(C + "\n-------------------- " + s + " --------------------" + R);
    }

    private static void ok(String s) {
        System.out.println(G + "✅ " + s + R);
    }

    private static void boom(String s) {
        System.out.println(RED + "****************************************************************" + R);
        System.out.println(RED + "❌ " + s + R);
        System.out.println(RED + "****************************************************************" + R);
    }

    private static void send(String op, String json) {
        System.out.println("📤 [" + op + "] Request JSON:");
        System.out.println(json);
        line();
    }

    private static void recv(String op, String json) {
        System.out.println("📥 [" + op + "] Response JSON:");
        System.out.println(json);
        line();
    }

    private static void assert200(String op, String resp) {
        int st = JsonParsers.status(resp);
        try {
            assertEquals(200, st, op + ": expected status=200, but got=" + st + ", resp=" + resp);
            ok(op + ": status=200");
        } catch (AssertionError ae) {
            boom(op + ": ожидали 200, но получили " + st);
            throw ae;
        }
    }

    @BeforeAll
    static void ensureUserExists() {
        title("AddBlockIT (BeforeAll): предусловие — пользователь должен существовать (AddUser: 200 или 409)");

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            String reqId = "it03-adduser-beforeall";
            String reqJson = JsonBuilders.addUser(reqId);

            send("AddUser(BeforeAll)", reqJson);
            String resp = client.request(reqId, reqJson, Duration.ofSeconds(5));
            recv("AddUser(BeforeAll)", resp);

            int st = JsonParsers.status(resp);

            if (st == 200) {
                ok("BeforeAll: пользователь создан/добавлен (status=200)");
            } else if (st == 409) {
                String code = JsonParsers.errorCode(resp);
                if ("USER_ALREADY_EXISTS".equals(code)) {
                    ok("BeforeAll: пользователь уже есть (status=409, USER_ALREADY_EXISTS)");
                } else {
                    boom("BeforeAll: status=409, но code неожиданный: " + code);
                    fail("User precondition failed. status=409, code=" + code + ", resp=" + resp);
                }
            } else {
                boom("BeforeAll: предусловие не выполнено. status=" + st);
                fail("User precondition failed. status=" + st + ", resp=" + resp);
            }
        }
    }

    @Test
    void addBlock_shouldAppendHeaderThenText() {
        title("AddBlockIT: добавить HEADER(0) и затем TEXT(1) без auth — с проверкой serverLastGlobalHash");
        System.out.println("Ожидание:");
        System.out.println("  1) AddBlock HEADER (global=0, prev=ZERO64) -> 200");
        System.out.println("     - в ответе payload.serverLastGlobalHash (64 hex)");
        System.out.println("  2) AddBlock TEXT   (global=1, prev=serverLastGlobalHash) -> 200\n");

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {

            // -------------------- ШАГ 1: HEADER (global=0) --------------------
            stepTitle("ШАГ 1: AddBlock HEADER (global=0)");

            byte[] headerFull = buildHeaderBlockFullBytes(
                    0,
                    (short) 0,
                    0,
                    ZERO32,
                    ZERO32
            );

            String reqId1 = "it03-add-header";
            String reqJson1 = buildAddBlockJson(reqId1, TestConfig.TEST_BCH_NAME(), 0, ZERO64, base64(headerFull));

            send("AddBlock#HEADER", reqJson1);
            String resp1 = client.request(reqId1, reqJson1, Duration.ofSeconds(8));
            recv("AddBlock#HEADER", resp1);

            assert200("AddBlock#HEADER", resp1);

            String serverLastGlobalHash = extractPayloadString(resp1, "serverLastGlobalHash");
            assertNotNull(serverLastGlobalHash, "HEADER: payload.serverLastGlobalHash must not be null");
            assertFalse(serverLastGlobalHash.isBlank(), "HEADER: payload.serverLastGlobalHash must not be blank");
            assertEquals(64, serverLastGlobalHash.trim().length(), "HEADER: serverLastGlobalHash must be 64 hex chars");

            ok("HEADER принят. serverLastGlobalHash=" + serverLastGlobalHash);

            // -------------------- ШАГ 2: TEXT (global=1) --------------------
            stepTitle("ШАГ 2: AddBlock TEXT (global=1)");

            byte[] prevGlobal32 = hexToBytes32(serverLastGlobalHash);
            byte[] prevLine32   = prevGlobal32;

            byte[] textFull = buildTextBlockFullBytes(
                    1,
                    (short) 0,
                    1,
                    prevGlobal32,
                    prevLine32,
                    "Hello from IT_03 test"
            );

            String reqId2 = "it03-add-text";
            String reqJson2 = buildAddBlockJson(reqId2, TestConfig.TEST_BCH_NAME(), 1, serverLastGlobalHash, base64(textFull));

            send("AddBlock#TEXT", reqJson2);
            String resp2 = client.request(reqId2, reqJson2, Duration.ofSeconds(8));
            recv("AddBlock#TEXT", resp2);

            assert200("AddBlock#TEXT", resp2);

            ok("ТЕСТ ПРОЙДЕН: AddBlock HEADER(0) + TEXT(1) успешно добавлены");

        } catch (AssertionError | RuntimeException e) {
            boom("ТЕСТ УПАЛ: AddBlockIT. Причина: " + e.getMessage());
            throw e;
        }
    }

    // =================================================================================
    // BUILD BLOCKS
    // =================================================================================

    private static byte[] buildHeaderBlockFullBytes(int globalNumber,
                                                    short lineIndex,
                                                    int lineBlockNumber,
                                                    byte[] prevGlobalHash32,
                                                    byte[] prevLineHash32) {

        HeaderBody body = new HeaderBody(TestConfig.TEST_LOGIN());
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
                TestConfig.TEST_LOGIN(),
                prevGlobalHash32,
                prevLineHash32,
                rawBytes
        );

        byte[] hash32 = BchCryptoVerifier.sha256(preimage);

        // Подпись делаем ключом логина
        byte[] signature64 = Ed25519Util.sign(hash32, TestConfig.LOGIN_PRIV_KEY());

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

    private static String buildAddBlockJson(String requestId,
                                           String blockchainName,
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
            """.formatted(requestId, blockchainName, globalNumber, prevGlobalHashHex, blockBytesB64);
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