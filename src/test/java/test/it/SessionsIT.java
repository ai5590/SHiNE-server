package test.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionsIT {

    @BeforeAll
    static void ensureUserExists() {
        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            String reqId = "it-adduser-beforeall";
            String resp = client.request(reqId, JsonBuilders.addUser(reqId), Duration.ofSeconds(5));
            int st = JsonParsers.status(resp);

            // 200 или "уже есть" — ок
            if (!(st == 200 || st == 409)) {
                fail("User precondition failed. status=" + st + ", resp=" + resp);
            }
        }
    }

    @Test
    void sessions_flow_shouldCreateListRefreshCloseCorrectly() {
        String s1Id, s1Pwd;
        String s2Id, s2Pwd;

        // --- create session1 ---
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-1";
            String resp1 = c.request(r1, JsonBuilders.authChallenge(r1), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp1));
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);

            String r2 = "it-create-1";
            String storagePwd = TestConfig.fakeStoragePwd();
            String resp2 = c.request(r2, JsonBuilders.createAuthSession(r2, nonce, storagePwd), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp2));

            s1Id = JsonParsers.sessionId(resp2);
            s1Pwd = JsonParsers.sessionPwd(resp2);
            assertNotNull(s1Id);
            assertNotNull(s1Pwd);
        }

        // --- create session2 and list inside (AUTH_STATUS_USER) ---
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-2";
            String resp1 = c.request(r1, JsonBuilders.authChallenge(r1), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp1));
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);

            String r2 = "it-create-2";
            String resp2 = c.request(r2, JsonBuilders.createAuthSession(r2, nonce, TestConfig.fakeStoragePwd()), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp2));

            s2Id = JsonParsers.sessionId(resp2);
            s2Pwd = JsonParsers.sessionPwd(resp2);
            assertNotNull(s2Id);
            assertNotNull(s2Pwd);

            // list inside session2 (у тебя это AUTH_STATUS_USER без подписи)
            String r3 = "it-list-in-session2";
            String resp3 = c.request(r3, JsonBuilders.listSessions(r3, 0L, ""), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp3));
            List<String> ids = JsonParsers.sessionIds(resp3);

            assertTrue(ids.contains(s1Id), "Must contain session1");
            assertTrue(ids.contains(s2Id), "Must contain session2");
        }

        // --- list in AUTH_IN_PROGRESS (подпись по nonce) ---
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-list";
            String resp1 = c.request(r1, JsonBuilders.authChallenge(r1), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp1));
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);

            long timeMs = System.currentTimeMillis();
            String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

            String r2 = "it-list-auth-in-progress";
            String resp2 = c.request(r2, JsonBuilders.listSessions(r2, timeMs, sig), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp2));

            List<String> ids = JsonParsers.sessionIds(resp2);
            assertTrue(ids.contains(s1Id));
            assertTrue(ids.contains(s2Id));
        }

        // --- refresh session1 and close session2 (from session1) ---
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {

            String r1 = "it-refresh-s1";
            String resp1 = c.request(r1, JsonBuilders.refreshSession(r1, s1Id, s1Pwd), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp1));
            assertNotNull(JsonParsers.storagePwd(resp1));

            String r2 = "it-close-s2";
            String resp2 = c.request(r2, JsonBuilders.closeActiveSession(r2, s2Id, 0L, ""), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp2));
        }

        // --- verify only session1 remains (AUTH_IN_PROGRESS list) ---
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-list2";
            String resp1 = c.request(r1, JsonBuilders.authChallenge(r1), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp1));
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);

            long timeMs = System.currentTimeMillis();
            String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

            String r2 = "it-list-after-close-s2";
            String resp2 = c.request(r2, JsonBuilders.listSessions(r2, timeMs, sig), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp2));

            List<String> ids = JsonParsers.sessionIds(resp2);
            assertTrue(ids.contains(s1Id));
            assertFalse(ids.contains(s2Id));
        }

        // --- close session1 in AUTH_IN_PROGRESS ---
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-close-s1";
            String resp1 = c.request(r1, JsonBuilders.authChallenge(r1), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp1));
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);

            long timeMs = System.currentTimeMillis();
            String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

            String r2 = "it-close-s1";
            String resp2 = c.request(r2, JsonBuilders.closeActiveSession(r2, s1Id, timeMs, sig), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp2));
        }

        // --- verify empty list ---
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-list-empty";
            String resp1 = c.request(r1, JsonBuilders.authChallenge(r1), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp1));
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);

            long timeMs = System.currentTimeMillis();
            String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

            String r2 = "it-list-empty";
            String resp2 = c.request(r2, JsonBuilders.listSessions(r2, timeMs, sig), Duration.ofSeconds(5));
            assertEquals(200, JsonParsers.status(resp2));

            List<String> ids = JsonParsers.sessionIds(resp2);
            assertTrue(ids.isEmpty(), "Sessions must be empty");
        }
    }
}