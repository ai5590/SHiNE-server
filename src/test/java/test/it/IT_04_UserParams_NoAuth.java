package test.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import test.it.utils.*;
import utils.config.ShineSignatureConstants;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_04_UserParams_NoAuth
 *
 * Сценарий:
 *  1) UpsertUserParam: сохранить param1
 *  2) GetUserParam: получить param1 и проверить поля
 *  3) UpsertUserParam: сохранить param2
 *  4) UpsertUserParam: обновить param1 (time_ms больше)
 *  5) ListUserParams: получить список и проверить:
 *      - есть param1 (обновлённое значение/time)
 *      - есть param2
 *
 * Примечание по безопасности (на будущее):
 *  - сейчас (MVP) чтение/запись параметров без ограничений по сессии.
 *  - позже можно добавить: доступ только владельцу или доверенным, через active_session/ACL.
 */
public class IT_04_UserParams_NoAuth {

    private static final ObjectMapper M = new ObjectMapper();

    public static void main(String[] args) {
        int failed = run();
        // System.exit(failed);
    }

    public static int run() {
        return TestLog.runOne("IT_04_UserParams_NoAuth", IT_04_UserParams_NoAuth::testBody);
    }

    @BeforeAll
    static void init() {
        ItRunContext.initIfNeeded();
    }

    private static void testBody() {
        ItRunContext.initIfNeeded();

        Duration timeout = Duration.ofSeconds(5);

        // ---------------------------------------------------------
        // ensure user exists (как в твоих тестах: 200 или 409)
        // ---------------------------------------------------------
        addUserOr409AlreadyExists(
                "USER1",
                TestConfig.LOGIN(),
                TestConfig.BCH_NAME(),
                TestConfig.LOGIN_PUBKEY_B64(),
                TestConfig.DEVICE_PUBKEY_B64()
        );

        final String login = TestConfig.LOGIN();
        final String deviceKeyB64 = TestConfig.DEVICE_PUBKEY_B64();
        final byte[] devicePrivKey = TestConfig.DEVICE_PRIV_KEY(); // важно: подпись именно device-ключом

        // ---------------------------------------------------------
        // 1) сохранить param1
        // ---------------------------------------------------------
        final String p1 = "profile:name";
        final String v1 = "Anna";
        final long t1 = System.currentTimeMillis();

        upsertUserParam_OK(login, p1, t1, v1, deviceKeyB64, devicePrivKey, timeout);

        // ---------------------------------------------------------
        // 2) получить param1 и проверить
        // ---------------------------------------------------------
        NetParam got1 = getUserParam_200(login, p1, timeout);

        assertEquals(login, got1.login);
        assertEquals(p1, got1.param);
        assertEquals(t1, got1.timeMs);
        assertEquals(v1, got1.value);
        assertEquals(deviceKeyB64, got1.deviceKeyB64);
        assertNotNull(got1.signatureB64);
        assertFalse(got1.signatureB64.isBlank());

        // ---------------------------------------------------------
        // 3) сохранить param2
        // ---------------------------------------------------------
        final String p2 = "profile:city";
        final String v2 = "Amsterdam";
        final long t2 = t1 + 10;

        upsertUserParam_OK(login, p2, t2, v2, deviceKeyB64, devicePrivKey, timeout);

        // ---------------------------------------------------------
        // 4) обновить param1 более новым временем
        // ---------------------------------------------------------
        final String v1b = "Anna Updated";
        final long t1b = t2 + 10;

        upsertUserParam_OK(login, p1, t1b, v1b, deviceKeyB64, devicePrivKey, timeout);

        // доп.проверка: GetUserParam теперь должен вернуть обновлённое
        NetParam got1b = getUserParam_200(login, p1, timeout);
        assertEquals(t1b, got1b.timeMs);
        assertEquals(v1b, got1b.value);

        // ---------------------------------------------------------
        // 5) list всех параметров и проверка состава
        // ---------------------------------------------------------
        NetParamList list = listUserParams_200(login, timeout);

        NetParam lp1 = list.find(p1);
        NetParam lp2 = list.find(p2);

        assertNotNull(lp1, "ListUserParams должен содержать param1=" + p1);
        assertNotNull(lp2, "ListUserParams должен содержать param2=" + p2);

        assertEquals(t1b, lp1.timeMs, "param1 должен быть обновлённым");
        assertEquals(v1b, lp1.value, "param1 должен иметь обновлённое значение");

        assertEquals(t2, lp2.timeMs);
        assertEquals(v2, lp2.value);

        // и у обоих должны возвращаться все поля из БД (как ты просил)
        assertEquals(deviceKeyB64, lp1.deviceKeyB64);
        assertEquals(deviceKeyB64, lp2.deviceKeyB64);
        assertNotNull(lp1.signatureB64);
        assertNotNull(lp2.signatureB64);

        TestLog.pass("IT_04_UserParams_NoAuth: OK");
    }

    // =================================================================================
    // WS helpers: Upsert/Get/List
    // =================================================================================

    private static void upsertUserParam_OK(String login,
                                          String param,
                                          long timeMs,
                                          String value,
                                          String deviceKeyB64,
                                          byte[] devicePrivKey,
                                          Duration timeout) {

        String signatureB64 = signUserParam(devicePrivKey, login, param, timeMs, value);

        String reqId = "it-upsert-" + param.replace(':', '_');

        String reqJson = """
                {
                  "op": "UpsertUserParam",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s",
                    "param": "%s",
                    "time_ms": %d,
                    "value": "%s",
                    "device_key": "%s",
                    "signature": "%s"
                  }
                }
                """.formatted(
                reqId,
                login,
                param,
                timeMs,
                jsonEscape(value),
                deviceKeyB64,
                signatureB64
        );

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            TestLog.send("UpsertUserParam", reqJson);
            String resp = client.request(reqId, reqJson, timeout);
            TestLog.recv("UpsertUserParam", resp);

            int st = JsonParsers.status(resp);
            assertEquals(200, st, "UpsertUserParam expected 200, resp=" + resp);
        }
    }

    private static NetParam getUserParam_200(String login, String param, Duration timeout) {
        String reqId = "it-get-" + param.replace(':', '_');

        String reqJson = """
                {
                  "op": "GetUserParam",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s",
                    "param": "%s"
                  }
                }
                """.formatted(reqId, login, param);

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            TestLog.send("GetUserParam", reqJson);
            String resp = client.request(reqId, reqJson, timeout);
            TestLog.recv("GetUserParam", resp);

            int st = JsonParsers.status(resp);
            assertEquals(200, st, "GetUserParam expected 200, resp=" + resp);

            return parseParamFromResponsePayload(resp);
        }
    }

    private static NetParamList listUserParams_200(String login, Duration timeout) {
        String reqId = "it-list-params";

        String reqJson = """
                {
                  "op": "ListUserParams",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s"
                  }
                }
                """.formatted(reqId, login);

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            TestLog.send("ListUserParams", reqJson);
            String resp = client.request(reqId, reqJson, timeout);
            TestLog.recv("ListUserParams", resp);

            int st = JsonParsers.status(resp);
            assertEquals(200, st, "ListUserParams expected 200, resp=" + resp);

            return parseParamListFromResponsePayload(resp);
        }
    }

    // =================================================================================
    // Parsing helpers
    // =================================================================================

    private static NetParam parseParamFromResponsePayload(String respJson) {
        try {
            JsonNode root = M.readTree(respJson);
            JsonNode payload = root.get("payload");
            assertNotNull(payload, "payload is null: " + respJson);

            NetParam p = new NetParam();
            p.login = text(payload, "login");
            p.param = text(payload, "param");
            p.timeMs = longVal(payload, "time_ms");
            p.value = text(payload, "value");
            p.deviceKeyB64 = text(payload, "device_key");
            p.signatureB64 = text(payload, "signature");
            return p;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse GetUserParam response: " + respJson, e);
        }
    }

    private static NetParamList parseParamListFromResponsePayload(String respJson) {
        try {
            JsonNode root = M.readTree(respJson);
            JsonNode payload = root.get("payload");
            assertNotNull(payload, "payload is null: " + respJson);

            NetParamList out = new NetParamList();
            out.login = text(payload, "login");

            JsonNode arr = payload.get("params");
            assertNotNull(arr, "payload.params is null: " + respJson);
            assertTrue(arr.isArray(), "payload.params must be array: " + respJson);

            for (JsonNode it : arr) {
                NetParam p = new NetParam();
                p.login = text(it, "login");
                p.param = text(it, "param");
                p.timeMs = longVal(it, "time_ms");
                p.value = text(it, "value");
                p.deviceKeyB64 = text(it, "device_key");
                p.signatureB64 = text(it, "signature");
                out.items = out.itemsAppend(p);
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ListUserParams response: " + respJson, e);
        }
    }

    private static String text(JsonNode obj, String field) {
        JsonNode v = obj.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static long longVal(JsonNode obj, String field) {
        JsonNode v = obj.get(field);
        if (v == null || v.isNull()) return 0;
        return v.asLong();
    }

    // =================================================================================
    // Signature + JSON string helpers
    // =================================================================================

    private static String signUserParam(byte[] devicePrivKey,
                                        String login,
                                        String param,
                                        long timeMs,
                                        String value) {

        String signText =
                ShineSignatureConstants.USER_PARAMETER_PREFIX +
                        login + param + timeMs + value;

        byte[] signBytes = signText.getBytes(StandardCharsets.UTF_8);

        // Важно: Ed25519Util.sign(...) ожидает (dataHash OR data?) — у тебя в проекте это уже устаканено.
        // В хэндлере verify(...) делается на signBytes напрямую, значит подписывать нужно signBytes.
        byte[] sig64 = Ed25519Util.sign(signBytes, devicePrivKey);
        return Base64.getEncoder().encodeToString(sig64);
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =================================================================================
    // AddUser helper (как у тебя)
    // =================================================================================

    private static void addUserOr409AlreadyExists(String label,
                                                  String login,
                                                  String blockchainName,
                                                  String loginPubKeyB64,
                                                  String devicePubKeyB64) {

        TestLog.title(label + ": AddUser (200 OK) или 409 USER_ALREADY_EXISTS");

        String reqId = "it-adduser-" + label.toLowerCase();

        String reqJson = """
                {
                  "op": "AddUser",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s",
                    "blockchainName": "%s",
                    "loginKey": "%s",
                    "deviceKey": "%s",
                    "bchLimit": %d
                  }
                }
                """.formatted(
                reqId,
                login,
                blockchainName,
                loginPubKeyB64,
                devicePubKeyB64,
                TestConfig.TEST_BCH_LIMIT
        );

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            TestLog.send("AddUser(" + label + ")", reqJson);
            String resp = client.request(reqId, reqJson, Duration.ofSeconds(5));
            TestLog.recv("AddUser(" + label + ")", resp);

            int st = JsonParsers.status(resp);
            if (st == 200) {
                TestLog.ok(label + ": создан/добавлен (status=200)");
            } else if (st == 409) {
                String code = JsonParsers.errorCode(resp);
                assertEquals("USER_ALREADY_EXISTS", code, label + ": expected USER_ALREADY_EXISTS, resp=" + resp);
                TestLog.ok(label + ": уже есть (status=409, USER_ALREADY_EXISTS)");
            } else {
                fail(label + ": неожиданный status=" + st + ", resp=" + resp);
            }
        }
    }

    // =================================================================================
    // Small DTOs
    // =================================================================================

    private static final class NetParam {
        String login;
        String param;
        long timeMs;
        String value;
        String deviceKeyB64;
        String signatureB64;
    }

    private static final class NetParamList {
        String login;
        NetParam[] items = new NetParam[0];

        NetParam[] itemsAppend(NetParam p) {
            NetParam[] n = new NetParam[items.length + 1];
            System.arraycopy(items, 0, n, 0, items.length);
            n[items.length] = p;
            items = n;
            return items;
        }

        NetParam find(String param) {
            for (NetParam p : items) {
                if (p != null && param.equals(p.param)) return p;
            }
            return null;
        }
    }
}