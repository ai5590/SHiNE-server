package test.it.cases;

import test.it.utils.TestConfig;
import test.it.utils.json.JsonBuilders;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestLog;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_02_Sessions
 *
 * Цель:
 *  - проверить создание/листинг/refresh/close
 *  - и после завершения оставить в БД 3 активных сессии (S1,S2,S3)
 */
public class IT_02_Sessions {

    private static final String LOGIN = TestConfig.LOGIN();

    public static void main(String[] args) {
        TestLog.info("Standalone: этот тест требует заранее созданных пользователей -> сначала запускаю IT_01_AddUser");
        System.out.println(IT_01_AddUser.run());
        String summary = run();
        System.out.println(summary);
    }

    public static String run() {
        TestResult r = new TestResult("IT_02_Sessions");

        Duration t = Duration.ofSeconds(5);

        String s1Id, s1Pwd;
        String s2Id, s2Pwd;
        String s3Id, s3Pwd;

        try {
            // 1) Создаём 3 сессии (каждая — отдельным соединением, чтобы не зависеть от состояния WS)
            Session s1 = createSession(LOGIN, t, r, "S1");
            s1Id = s1.sessionId; s1Pwd = s1.sessionPwd;

            Session s2 = createSession(LOGIN, t, r, "S2");
            s2Id = s2.sessionId; s2Pwd = s2.sessionPwd;

            Session s3 = createSession(LOGIN, t, r, "S3");
            s3Id = s3.sessionId; s3Pwd = s3.sessionPwd;

            // 2) ListSessions в AUTH_IN_PROGRESS — должны быть S1,S2,S3
            try (WsSession ws = WsSession.open()) {
                String nonceResp = ws.call("AuthChallenge(list)", JsonBuilders.authChallenge(LOGIN), t);
                assertEquals(200, JsonParsers.status(nonceResp), "AuthChallenge(list) must be 200");
                String nonce = JsonParsers.authNonce(nonceResp);
                assertNotNull(nonce, "authNonce must not be null");

                long timeMs = System.currentTimeMillis();
                String sig = JsonBuilders.signAuthorificated(nonce, timeMs, TestConfig.getDevicePrivatKey(LOGIN));

                String listResp = ws.call("ListSessions(AUTH_IN_PROGRESS)", JsonBuilders.listSessions(timeMs, sig), t);
                assertEquals(200, JsonParsers.status(listResp), "ListSessions must be 200");

                List<String> ids = JsonParsers.sessionIds(listResp);
                r.ok("ListSessions(AUTH_IN_PROGRESS): " + ids);

                assertTrue(ids.contains(s1Id), "Must contain S1");
                assertTrue(ids.contains(s2Id), "Must contain S2");
                assertTrue(ids.contains(s3Id), "Must contain S3");
                r.ok("Проверка OK: список содержит S1,S2,S3");
            }

            // 3) RefreshSession(S1) -> после refresh в этом же соединении делаем ListSessions(AUTH_STATUS_USER) (timeMs=0)
            try (WsSession ws = WsSession.open()) {
                String refreshResp = ws.call("RefreshSession(S1)", JsonBuilders.refreshSession(s1Id, s1Pwd), t);
                assertEquals(200, JsonParsers.status(refreshResp), "RefreshSession(S1) must be 200");
                assertNotNull(JsonParsers.storagePwd(refreshResp), "storagePwd must not be null");
                r.ok("RefreshSession(S1): OK");

                String listInUserResp = ws.call("ListSessions(AUTH_STATUS_USER)", JsonBuilders.listSessions(0L, ""), t);
                assertEquals(200, JsonParsers.status(listInUserResp), "ListSessions(AUTH_STATUS_USER) must be 200");

                List<String> ids = JsonParsers.sessionIds(listInUserResp);
                r.ok("ListSessions(AUTH_STATUS_USER): " + ids);

                assertTrue(ids.contains(s1Id));
                assertTrue(ids.contains(s2Id));
                assertTrue(ids.contains(s3Id));
                r.ok("Проверка OK: AUTH_STATUS_USER список содержит S1,S2,S3");
            }

            // 4) Проверяем CloseActiveSession, но так, чтобы итогом всё равно осталось 3 сессии:
            //    создаём TEMP, закрываем TEMP, убеждаемся что S1,S2,S3 остались.
            Session temp = createSession(LOGIN, t, r, "TEMP");
            String tempId = temp.sessionId;

            try (WsSession ws = WsSession.open()) {
                String nonceResp = ws.call("AuthChallenge(close TEMP)", JsonBuilders.authChallenge(LOGIN), t);
                assertEquals(200, JsonParsers.status(nonceResp), "AuthChallenge(close TEMP) must be 200");
                String nonce = JsonParsers.authNonce(nonceResp);
                assertNotNull(nonce);

                long timeMs = System.currentTimeMillis();
                String sig = JsonBuilders.signAuthorificated(nonce, timeMs, TestConfig.getDevicePrivatKey(LOGIN));

                String closeResp = ws.call("CloseActiveSession(TEMP)", JsonBuilders.closeActiveSession(tempId, timeMs, sig), t);
                assertEquals(200, JsonParsers.status(closeResp), "CloseActiveSession(TEMP) must be 200");
                r.ok("CloseActiveSession(TEMP): OK");
            }

            // 5) Финальная проверка: снова ListSessions(AUTH_IN_PROGRESS) => S1,S2,S3 должны остаться, TEMP нет
            try (WsSession ws = WsSession.open()) {
                String nonceResp = ws.call("AuthChallenge(final list)", JsonBuilders.authChallenge(LOGIN), t);
                assertEquals(200, JsonParsers.status(nonceResp));
                String nonce = JsonParsers.authNonce(nonceResp);
                assertNotNull(nonce);

                long timeMs = System.currentTimeMillis();
                String sig = JsonBuilders.signAuthorificated(nonce, timeMs, TestConfig.getDevicePrivatKey(LOGIN));

                String listResp = ws.call("ListSessions(final AUTH_IN_PROGRESS)", JsonBuilders.listSessions(timeMs, sig), t);
                assertEquals(200, JsonParsers.status(listResp));

                List<String> ids = JsonParsers.sessionIds(listResp);
                r.ok("Final ListSessions: " + ids);

                assertTrue(ids.contains(s1Id));
                assertTrue(ids.contains(s2Id));
                assertTrue(ids.contains(s3Id));
                assertFalse(ids.contains(tempId));
                r.ok("ИТОГ OK: после теста в БД остались 3 активные сессии (S1,S2,S3)");
            }

        } catch (Throwable e) {
            r.fail("IT_02_Sessions упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    private static Session createSession(String login, Duration t, TestResult r, String label) {
        try (WsSession ws = WsSession.open()) {
            String nonceResp = ws.call("AuthChallenge(" + label + ")", JsonBuilders.authChallenge(login), t);
            assertEquals(200, JsonParsers.status(nonceResp), "AuthChallenge(" + label + ") must be 200");
            String nonce = JsonParsers.authNonce(nonceResp);
            assertNotNull(nonce, "authNonce must not be null for " + label);

            String createResp = ws.call("CreateAuthSession(" + label + ")", JsonBuilders.createAuthSession(login, nonce, TestConfig.fakeStoragePwd()), t);
            assertEquals(200, JsonParsers.status(createResp), "CreateAuthSession(" + label + ") must be 200");

            String sid = JsonParsers.sessionId(createResp);
            String spw = JsonParsers.sessionPwd(createResp);

            assertNotNull(sid, "sessionId must not be null");
            assertNotNull(spw, "sessionPwd must not be null");

            r.ok("Создана сессия " + label + ": sessionId=" + sid);
            return new Session(sid, spw);
        }
    }

    private record Session(String sessionId, String sessionPwd) {}
}