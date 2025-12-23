package test.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionsIT {

    // ANSI цвета
    private static final String R = "\u001B[0m";
    private static final String G = "\u001B[32m";
    private static final String Y = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String C = "\u001B[36m";

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

    private static void warn(String s) {
        System.out.println(Y + "⚠️ " + s + R);
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
        title("SessionsIT (BeforeAll): предусловие — пользователь должен существовать (AddUser: 200 или 409)");

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            String reqId = "it-adduser-beforeall";
            String reqJson = JsonBuilders.addUser(reqId);

            send("AddUser(BeforeAll)", reqJson);
            String resp = client.request(reqId, reqJson, Duration.ofSeconds(5));
            recv("AddUser(BeforeAll)", resp);

            int st = JsonParsers.status(resp);

            // 200 или "уже есть" — ок
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
    void sessions_flow_shouldCreateListRefreshCloseCorrectly() {
        title("SessionsIT: полный сценарий сессий (создать 2, проверить list, refresh/close, проверить очистку)");
        System.out.println("Ожидание сценария:");
        System.out.println("  1) Создаём SESSION1 через AuthChallenge + CreateAuthSession");
        System.out.println("  2) Создаём SESSION2 и делаем ListSessions внутри неё (AUTH_STATUS_USER) → должны быть SESSION1 и SESSION2");
        System.out.println("  3) Делаем ListSessions в AUTH_IN_PROGRESS (подпись по nonce) → должны быть SESSION1 и SESSION2");
        System.out.println("  4) Refresh SESSION1 (входим в AUTH_STATUS_USER) и Close SESSION2");
        System.out.println("  5) Проверяем ListSessions (AUTH_IN_PROGRESS) → осталась только SESSION1");
        System.out.println("  6) Закрываем SESSION1 в AUTH_IN_PROGRESS");
        System.out.println("  7) Проверяем ListSessions → пусто\n");

        String s1Id, s1Pwd;
        String s2Id, s2Pwd;

        try {
            // --- create session1 ---
            stepTitle("ШАГ 1: создать SESSION1 (AuthChallenge -> CreateAuthSession)");
            try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
                String r1 = "it-auth-1";
                String req1 = JsonBuilders.authChallenge(r1);
                send("AuthChallenge#1", req1);
                String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
                recv("AuthChallenge#1", resp1);

                assert200("AuthChallenge#1", resp1);
                String nonce = JsonParsers.authNonce(resp1);
                assertNotNull(nonce, "AuthChallenge#1: nonce must not be null");
                ok("AuthChallenge#1: authNonce получен: " + nonce);

                String r2 = "it-create-1";
                String storagePwd = TestConfig.fakeStoragePwd();
                String req2 = JsonBuilders.createAuthSession(r2, nonce, storagePwd);
                send("CreateAuthSession#1", req2);
                String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
                recv("CreateAuthSession#1", resp2);

                assert200("CreateAuthSession#1", resp2);

                s1Id = JsonParsers.sessionId(resp2);
                s1Pwd = JsonParsers.sessionPwd(resp2);
                assertNotNull(s1Id, "CreateAuthSession#1: sessionId must not be null");
                assertNotNull(s1Pwd, "CreateAuthSession#1: sessionPwd must not be null");
                ok("SESSION1 получена: sessionId=" + s1Id + ", sessionPwd=[получен]");
            }

            // --- create session2 and list inside (AUTH_STATUS_USER) ---
            stepTitle("ШАГ 2: создать SESSION2 и ListSessions внутри неё (AUTH_STATUS_USER) → должны быть SESSION1+SESSION2");
            try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
                String r1 = "it-auth-2";
                String req1 = JsonBuilders.authChallenge(r1);
                send("AuthChallenge#2", req1);
                String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
                recv("AuthChallenge#2", resp1);

                assert200("AuthChallenge#2", resp1);
                String nonce = JsonParsers.authNonce(resp1);
                assertNotNull(nonce);
                ok("AuthChallenge#2: authNonce получен: " + nonce);

                String r2 = "it-create-2";
                String req2 = JsonBuilders.createAuthSession(r2, nonce, TestConfig.fakeStoragePwd());
                send("CreateAuthSession#2", req2);
                String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
                recv("CreateAuthSession#2", resp2);

                assert200("CreateAuthSession#2", resp2);

                s2Id = JsonParsers.sessionId(resp2);
                s2Pwd = JsonParsers.sessionPwd(resp2);
                assertNotNull(s2Id);
                assertNotNull(s2Pwd);
                ok("SESSION2 получена: sessionId=" + s2Id + ", sessionPwd=[получен]");

                // list inside session2 (у тебя это AUTH_STATUS_USER без подписи)
                String r3 = "it-list-in-session2";
                String req3 = JsonBuilders.listSessions(r3, 0L, "");
                send("ListSessions(in SESSION2)", req3);
                String resp3 = c.request(r3, req3, Duration.ofSeconds(5));
                recv("ListSessions(in SESSION2)", resp3);

                assert200("ListSessions(in SESSION2)", resp3);
                List<String> ids = JsonParsers.sessionIds(resp3);
                ok("ListSessions(in SESSION2): sessions=" + ids);

                assertTrue(ids.contains(s1Id), "Must contain session1");
                assertTrue(ids.contains(s2Id), "Must contain session2");
                ok("Проверка OK: список содержит SESSION1 и SESSION2");
            }

            // --- list in AUTH_IN_PROGRESS (подпись по nonce) ---
            stepTitle("ШАГ 3: ListSessions в AUTH_IN_PROGRESS (nonce+signature) → должны быть SESSION1+SESSION2");
            try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
                String r1 = "it-auth-list";
                String req1 = JsonBuilders.authChallenge(r1);
                send("AuthChallenge(list)", req1);
                String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
                recv("AuthChallenge(list)", resp1);

                assert200("AuthChallenge(list)", resp1);
                String nonce = JsonParsers.authNonce(resp1);
                assertNotNull(nonce);
                ok("AuthChallenge(list): authNonce=" + nonce);

                long timeMs = System.currentTimeMillis();
                String sig = JsonBuilders.signAuthorificated(nonce, timeMs);
                ok("Подпись для AUTH_IN_PROGRESS: timeMs=" + timeMs + ", signatureB64=[сгенерирована]");

                String r2 = "it-list-auth-in-progress";
                String req2 = JsonBuilders.listSessions(r2, timeMs, sig);
                send("ListSessions(AUTH_IN_PROGRESS)", req2);
                String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
                recv("ListSessions(AUTH_IN_PROGRESS)", resp2);

                assert200("ListSessions(AUTH_IN_PROGRESS)", resp2);

                List<String> ids = JsonParsers.sessionIds(resp2);
                ok("ListSessions(AUTH_IN_PROGRESS): sessions=" + ids);

                assertTrue(ids.contains(s1Id));
                assertTrue(ids.contains(s2Id));
                ok("Проверка OK: AUTH_IN_PROGRESS список содержит SESSION1 и SESSION2");
            }

            // --- refresh session1 and close session2 (from session1) ---
            stepTitle("ШАГ 4: Refresh SESSION1 (входим) и Close SESSION2 (из SESSION1)");
            try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {

                String r1 = "it-refresh-s1";
                String req1 = JsonBuilders.refreshSession(r1, s1Id, s1Pwd);
                send("RefreshSession(SESSION1)", req1);
                String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
                recv("RefreshSession(SESSION1)", resp1);

                assert200("RefreshSession(SESSION1)", resp1);
                assertNotNull(JsonParsers.storagePwd(resp1));
                ok("RefreshSession: storagePwd получен");

                String r2 = "it-close-s2";
                String req2 = JsonBuilders.closeActiveSession(r2, s2Id, 0L, "");
                send("CloseActiveSession(SESSION2)", req2);
                String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
                recv("CloseActiveSession(SESSION2)", resp2);

                assert200("CloseActiveSession(SESSION2)", resp2);
                ok("SESSION2 закрыта");
            }

            // --- verify only session1 remains (AUTH_IN_PROGRESS list) ---
            stepTitle("ШАГ 5: ListSessions(AUTH_IN_PROGRESS) → должна остаться только SESSION1");
            try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
                String r1 = "it-auth-list2";
                String req1 = JsonBuilders.authChallenge(r1);
                send("AuthChallenge(list2)", req1);
                String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
                recv("AuthChallenge(list2)", resp1);

                assert200("AuthChallenge(list2)", resp1);
                String nonce = JsonParsers.authNonce(resp1);
                assertNotNull(nonce);

                long timeMs = System.currentTimeMillis();
                String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

                String r2 = "it-list-after-close-s2";
                String req2 = JsonBuilders.listSessions(r2, timeMs, sig);
                send("ListSessions(after close S2)", req2);
                String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
                recv("ListSessions(after close S2)", resp2);

                assert200("ListSessions(after close S2)", resp2);

                List<String> ids = JsonParsers.sessionIds(resp2);
                ok("ListSessions(after close S2): sessions=" + ids);

                assertTrue(ids.contains(s1Id));
                assertFalse(ids.contains(s2Id));
                ok("Проверка OK: осталась только SESSION1");
            }

            // --- close session1 in AUTH_IN_PROGRESS ---
            stepTitle("ШАГ 6: Close SESSION1 в AUTH_IN_PROGRESS");
            try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
                String r1 = "it-auth-close-s1";
                String req1 = JsonBuilders.authChallenge(r1);
                send("AuthChallenge(close S1)", req1);
                String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
                recv("AuthChallenge(close S1)", resp1);

                assert200("AuthChallenge(close S1)", resp1);
                String nonce = JsonParsers.authNonce(resp1);
                assertNotNull(nonce);

                long timeMs = System.currentTimeMillis();
                String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

                String r2 = "it-close-s1";
                String req2 = JsonBuilders.closeActiveSession(r2, s1Id, timeMs, sig);
                send("CloseActiveSession(SESSION1)", req2);
                String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
                recv("CloseActiveSession(SESSION1)", resp2);

                assert200("CloseActiveSession(SESSION1)", resp2);
                ok("SESSION1 закрыта");
            }

            // --- verify empty list ---
            stepTitle("ШАГ 7: ListSessions(AUTH_IN_PROGRESS) → ожидаем пустой список");
            try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
                String r1 = "it-auth-list-empty";
                String req1 = JsonBuilders.authChallenge(r1);
                send("AuthChallenge(list empty)", req1);
                String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
                recv("AuthChallenge(list empty)", resp1);

                assert200("AuthChallenge(list empty)", resp1);
                String nonce = JsonParsers.authNonce(resp1);
                assertNotNull(nonce);

                long timeMs = System.currentTimeMillis();
                String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

                String r2 = "it-list-empty";
                String req2 = JsonBuilders.listSessions(r2, timeMs, sig);
                send("ListSessions(empty)", req2);
                String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
                recv("ListSessions(empty)", resp2);

                assert200("ListSessions(empty)", resp2);

                List<String> ids = JsonParsers.sessionIds(resp2);
                ok("ListSessions(empty): sessions=" + ids);

                assertTrue(ids.isEmpty(), "Sessions must be empty");
                ok("Проверка OK: список пуст");
            }

            ok("ТЕСТ ПРОЙДЕН ЦЕЛИКОМ: SessionsIT (весь сценарий сессий выполнен успешно)");

        } catch (AssertionError | RuntimeException e) {
            boom("ТЕСТ УПАЛ: SessionsIT. Причина: " + e.getMessage());
            throw e;
        }
    }
}
