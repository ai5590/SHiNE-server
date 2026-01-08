package test.it.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import test.it.utils.*;
import test.it.utils.TestConfig;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestLog;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;
import utils.config.ShineSignatureConstants;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_04_UserParams_NoAuth
 *
 * ВАЖНО:
 *  - пользователей НЕ создаём (их создаёт IT_01)
 */
public class IT_04_UserParams_NoAuth {

    private static final ObjectMapper M = new ObjectMapper();

    public static void main(String[] args) {
        TestLog.info("Standalone: этот тест требует заранее созданных пользователей -> сначала запускаю IT_01_AddUser");
        System.out.println(IT_01_AddUser.run());
        String summary = run();
        System.out.println(summary);
    }

    public static String run() {
        TestResult r = new TestResult("IT_04_UserParams_NoAuth");

        Duration timeout = Duration.ofSeconds(5);

        final String login = TestConfig.LOGIN();
        final String deviceKeyB64 = TestConfig.devicePublicKeyB64(login);
        final byte[] devicePrivKey = TestConfig.getDevicePrivatKey(login);

        try {
            // 1) сохранить param1
            final String p1 = "profile:name";
            final String v1 = "Anna";
            final long t1 = System.currentTimeMillis();
            upsertUserParam_OK(r, login, p1, t1, v1, deviceKeyB64, devicePrivKey, timeout);

            // 2) получить param1 и проверить
            NetParam got1 = getUserParam_200(r, login, p1, timeout);
            assertEquals(login, got1.login);
            assertEquals(p1, got1.param);
            assertEquals(t1, got1.timeMs);
            assertEquals(v1, got1.value);
            assertEquals(deviceKeyB64, got1.deviceKeyB64);
            assertNotNull(got1.signatureB64);
            assertFalse(got1.signatureB64.isBlank());
            r.ok("GetUserParam(param1) OK");

            // 3) сохранить param2
            final String p2 = "profile:city";
            final String v2 = "Amsterdam";
            final long t2 = t1 + 10;
            upsertUserParam_OK(r, login, p2, t2, v2, deviceKeyB64, devicePrivKey, timeout);

            // 4) обновить param1
            final String v1b = "Anna Updated";
            final long t1b = t2 + 10;
            upsertUserParam_OK(r, login, p1, t1b, v1b, deviceKeyB64, devicePrivKey, timeout);

            NetParam got1b = getUserParam_200(r, login, p1, timeout);
            assertEquals(t1b, got1b.timeMs);
            assertEquals(v1b, got1b.value);
            r.ok("GetUserParam(updated param1) OK");

            // 5) list всех параметров
            NetParamList list = listUserParams_200(r, login, timeout);

            NetParam lp1 = list.find(p1);
            NetParam lp2 = list.find(p2);

            assertNotNull(lp1, "ListUserParams должен содержать param1=" + p1);
            assertNotNull(lp2, "ListUserParams должен содержать param2=" + p2);

            assertEquals(t1b, lp1.timeMs);
            assertEquals(v1b, lp1.value);

            assertEquals(t2, lp2.timeMs);
            assertEquals(v2, lp2.value);

            assertEquals(deviceKeyB64, lp1.deviceKeyB64);
            assertEquals(deviceKeyB64, lp2.deviceKeyB64);
            assertNotNull(lp1.signatureB64);
            assertNotNull(lp2.signatureB64);

            r.ok("ListUserParams OK");

        } catch (Throwable e) {
            r.fail("IT_04 упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    // =================================================================================
    // WS helpers: Upsert/Get/List
    // =================================================================================

    private static void upsertUserParam_OK(TestResult r, String login, String param, long timeMs, String value, String deviceKeyB64, byte[] devicePrivKey, Duration timeout) {
        String signatureB64 = signUserParam(devicePrivKey, login, param, timeMs, value);

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
                """.formatted(TestIds.next("upsert"), login, param, timeMs, jsonEscape(value), deviceKeyB64, signatureB64);

        try (WsSession ws = WsSession.open()) {
            String resp = ws.call("UpsertUserParam(" + param + ")", reqJson, timeout);
            assertEquals(200, JsonParsers.status(resp), "UpsertUserParam expected 200, resp=" + resp);
            r.ok("UpsertUserParam(" + param + "): OK");
        }
    }

    private static NetParam getUserParam_200(TestResult r, String login, String param, Duration timeout) {
        String reqJson = """
                {
                  "op": "GetUserParam",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s",
                    "param": "%s"
                  }
                }
                """.formatted(TestIds.next("getparam"), login, param);

        try (WsSession ws = WsSession.open()) {
            String resp = ws.call("GetUserParam(" + param + ")", reqJson, timeout);
            assertEquals(200, JsonParsers.status(resp), "GetUserParam expected 200, resp=" + resp);
            r.ok("GetUserParam(" + param + "): OK");
            return parseParamFromResponsePayload(resp);
        }
    }

    private static NetParamList listUserParams_200(TestResult r, String login, Duration timeout) {
        String reqJson = """
                {
                  "op": "ListUserParams",
                  "requestId": "%s",
                  "payload": { "login": "%s" }
                }
                """.formatted(TestIds.next("listparams"), login);

        try (WsSession ws = WsSession.open()) {
            String resp = ws.call("ListUserParams", reqJson, timeout);
            assertEquals(200, JsonParsers.status(resp), "ListUserParams expected 200, resp=" + resp);
            r.ok("ListUserParams: OK");
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
    // Signature + JSON helpers
    // =================================================================================

    private static String signUserParam(byte[] devicePrivKey, String login, String param, long timeMs, String value) {
        String signText = ShineSignatureConstants.USER_PARAMETER_PREFIX + login + param + timeMs + value;
        byte[] signBytes = signText.getBytes(StandardCharsets.UTF_8);
        byte[] sig64 = Ed25519Util.sign(signBytes, devicePrivKey);
        return Base64.getEncoder().encodeToString(sig64);
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =================================================================================
    // DTOs
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