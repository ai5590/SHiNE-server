package test.it.utils.json;

import test.it.utils.TestIds;
import test.it.utils.TestConfig;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Builder'ы JSON запросов. Внутри автоматически генерим requestId. */
public final class JsonBuilders {
    private JsonBuilders() {}

    // ---------------- AddUser ----------------

    public static String addUser(String login) {
        String requestId = TestIds.next("adduser");
        String blockchainName = TestConfig.getBlockchainName(login);

        String solanaKeyB64 = TestConfig.solanaPublicKeyB64(login);
        String blockchainKeyB64 = TestConfig.blockchainPublicKeyB64(login);
        String deviceKeyB64 = TestConfig.devicePublicKeyB64(login);

        return """
                {
                  "op": "AddUser",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s",
                    "blockchainName": "%s",
                    "solanaKey": "%s",
                    "blockchainKey": "%s",
                    "deviceKey": "%s",
                    "bchLimit": %d
                  }
                }
                """.formatted(
                requestId,
                login,
                blockchainName,
                solanaKeyB64,
                blockchainKeyB64,
                deviceKeyB64,
                TestConfig.TEST_BCH_LIMIT
        );
    }

    // ---------------- AuthChallenge ----------------

    public static String authChallenge(String login) {
        String requestId = TestIds.next("auth");
        return """
                {
                  "op": "AuthChallenge",
                  "requestId": "%s",
                  "payload": { "login": "%s" }
                }
                """.formatted(requestId, login);
    }

    // ---------------- CreateAuthSession (v2) ----------------
    // v2: sessionKey генерируется на клиенте, на сервер отправляем только sessionPubKey (base64).
    // подпись шага CreateAuthSession всё ещё делается deviceKey: "AUTHORIFICATED:" + timeMs + authNonce

    public static String createAuthSessionV2(String login, String authNonce, String storagePwd, String sessionPubKeyB64) {
        long timeMs = System.currentTimeMillis();
        byte[] devicePriv = TestConfig.getDevicePrivatKey(login);
        String sigB64 = signAuthorificated(authNonce, timeMs, devicePriv);

        String requestId = TestIds.next("create");
        return """
                {
                  "op": "CreateAuthSession",
                  "requestId": "%s",
                  "payload": {
                    "storagePwd": "%s",
                    "sessionPubKeyB64": "%s",
                    "timeMs": %d,
                    "signatureB64": "%s",
                    "clientInfo": "%s"
                  }
                }
                """.formatted(
                requestId,
                storagePwd,
                sessionPubKeyB64,
                timeMs,
                sigB64,
                TestConfig.TEST_CLIENT_INFO
        );
    }

    // ---------------- SessionChallenge (v2) ----------------

    public static String sessionChallenge(String sessionId) {
        String requestId = TestIds.next("sch");
        return """
            {
              "op": "SessionChallenge",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s"
              }
            }
            """.formatted(requestId, sessionId);
    }

    // ---------------- SessionLogin (v2) ----------------

    public static String sessionLogin(String sessionId, String nonce, byte[] sessionPrivKey) {
        long timeMs = System.currentTimeMillis();
        String sigB64 = signSessionLogin(sessionId, timeMs, nonce, sessionPrivKey);

        String requestId = TestIds.next("slogin");
        return """
            {
              "op": "SessionLogin",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s",
                "timeMs": %d,
                "signatureB64": "%s",
                "clientInfo": "%s"
              }
            }
            """.formatted(requestId, sessionId, timeMs, sigB64, TestConfig.TEST_CLIENT_INFO);
    }

    // ---------------- ListSessions ----------------

    public static String listSessions(long timeMs, String signatureB64) {
        String requestId = TestIds.next("list");
        if (signatureB64 == null) signatureB64 = "";
        return """
            {
              "op": "ListSessions",
              "requestId": "%s",
              "payload": {
                "timeMs": %d,
                "signatureB64": "%s"
              }
            }
            """.formatted(requestId, timeMs, signatureB64);
    }

    // ---------------- CloseActiveSession ----------------

    public static String closeActiveSession(String sessionId, long timeMs, String signatureB64) {
        String requestId = TestIds.next("close");
        if (signatureB64 == null) signatureB64 = "";
        return """
            {
              "op": "CloseActiveSession",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s",
                "timeMs": %d,
                "signatureB64": "%s"
              }
            }
            """.formatted(requestId, sessionId, timeMs, signatureB64);
    }

    // ---------------- ListSubscribedChannels ----------------

    public static String listSubscribedChannels(String login) {
        String requestId = TestIds.next("subs");
        return """
        {
          "op": "ListSubscribedChannels",
          "requestId": "%s",
          "payload": { "login": "%s" }
        }
        """.formatted(requestId, login);
    }

    /**
     * Подпись для режима AUTH_IN_PROGRESS:
     * preimage = "AUTHORIFICATED:" + timeMs + authNonce
     * подписываем devicePrivKey.
     */
    public static String signAuthorificated(String authNonce, long timeMs, byte[] devicePrivKey) {
        String preimageStr = "AUTHORIFICATED:" + timeMs + authNonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);
        byte[] sig = Ed25519Util.sign(preimage, devicePrivKey);
        return Base64.getEncoder().encodeToString(sig);
    }

    /**
     * Подпись для SessionLogin(v2):
     * preimage = "SESSION_LOGIN:" + sessionId + ":" + timeMs + ":" + nonce
     * подписываем sessionPrivKey.
     */
    public static String signSessionLogin(String sessionId, long timeMs, String nonce, byte[] sessionPrivKey) {
        String preimageStr = "SESSION_LOGIN:" + sessionId + ":" + timeMs + ":" + nonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);
        byte[] sig = Ed25519Util.sign(preimage, sessionPrivKey);
        return Base64.getEncoder().encodeToString(sig);
    }
}