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
 * IT_02_Sessions (v2)
 *
 * Цель:
 *  - проверить создание/листинг/вход-в-сессию(2 шага)/close
 *  - и после завершения оставить в БД 3 активных сессии (S1,S2,S3)
 *
 * Протокол v2:
 *  - создание сессии: AuthChallenge -> CreateAuthSession (deviceKey подпись, + sessionPubKey)
 *  - вход в сессию: SessionChallenge(sessionId) -> nonce, затем SessionLogin(sessionId,time,signature(sessionKey))
 *  - ListSessions и CloseActiveSession доступны только в AUTH_STATUS_USER (после SessionLogin)
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
        TestResult r = new TestResult("IT_02_Sessions(v2)");

        Duration t = Duration.ofSeconds(5);

        Session s1, s2, s3;

        try {
            // 1) Создаём 3 сессии (каждая — отдельным соединением)
            s1 = createSession(LOGIN, t, r, "S1");
            s2 = createSession(LOGIN, t, r, "S2");
            s3 = createSession(LOGIN, t, r, "S3");

            // 2) Входим в S1 (2 шага) и делаем ListSessions (AUTH_STATUS_USER) — должны быть S1,S2,S3
            try (WsSession ws = WsSession.open()) {
                sessionLogin2Steps(ws, s1, t, "Login(S1)", r);

                String listResp = ws.call("ListSessions(AUTH_STATUS_USER)", JsonBuilders.listSessions(0L, ""), t);
                assertEquals(200, JsonParsers.status(listResp), "ListSessions(AUTH_STATUS_USER) must be 200");

                List<String> ids = JsonParsers.sessionIds(listResp);
                r.ok("ListSessions(AUTH_STATUS_USER): " + ids);

                assertTrue(ids.contains(s1.sessionId), "Must contain S1");
                assertTrue(ids.contains(s2.sessionId), "Must contain S2");
                assertTrue(ids.contains(s3.sessionId), "Must contain S3");
                r.ok("Проверка OK: список содержит S1,S2,S3");
            }

            // 3) Проверяем CloseActiveSession так, чтобы итогом всё равно осталось 3 сессии:
            //    создаём TEMP, логинимся в S1, закрываем TEMP, убеждаемся что S1,S2,S3 остались.
            Session temp = createSession(LOGIN, t, r, "TEMP");

            try (WsSession ws = WsSession.open()) {
                sessionLogin2Steps(ws, s1, t, "Login(S1) for close", r);

                String closeResp = ws.call("CloseActiveSession(TEMP)", JsonBuilders.closeActiveSession(temp.sessionId, 0L, ""), t);
                assertEquals(200, JsonParsers.status(closeResp), "CloseActiveSession(TEMP) must be 200");
                r.ok("CloseActiveSession(TEMP): OK");
            }

            // 4) Финальная проверка: снова логинимся в S1 и ListSessions => S1,S2,S3 должны остаться, TEMP нет
            try (WsSession ws = WsSession.open()) {
                sessionLogin2Steps(ws, s1, t, "Final Login(S1)", r);

                String listResp = ws.call("ListSessions(final)", JsonBuilders.listSessions(0L, ""), t);
                assertEquals(200, JsonParsers.status(listResp));

                List<String> ids = JsonParsers.sessionIds(listResp);
                r.ok("Final ListSessions: " + ids);

                assertTrue(ids.contains(s1.sessionId));
                assertTrue(ids.contains(s2.sessionId));
                assertTrue(ids.contains(s3.sessionId));
                assertFalse(ids.contains(temp.sessionId));
                r.ok("ИТОГ OK: после теста в БД остались 3 активные сессии (S1,S2,S3)");
            }

        } catch (Throwable e) {
            r.fail("IT_02_Sessions(v2) упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    private static Session createSession(String login, Duration t, TestResult r, String label) {
        try (WsSession ws = WsSession.open()) {

            // шаг 1: AuthChallenge
            String nonceResp = ws.call("AuthChallenge(" + label + ")", JsonBuilders.authChallenge(login), t);
            assertEquals(200, JsonParsers.status(nonceResp), "AuthChallenge(" + label + ") must be 200");
            String authNonce = JsonParsers.authNonce(nonceResp);
            assertNotNull(authNonce, "authNonce must not be null for " + label);

            // для тестов: sessionKey = deviceKey (в реале будет отдельный keypair)
            String sessionPubKeyB64 = TestConfig.devicePublicKeyB64(login);

            // storagePwd на клиенте (сохраняем, чтобы потом проверить, что сервер вернул именно его)
            String storagePwd = TestConfig.fakeStoragePwd();

            // шаг 2: CreateAuthSession (device подпись + sessionPubKey)
            String createResp = ws.call(
                    "CreateAuthSession(" + label + ")",
                    JsonBuilders.createAuthSessionV2(login, authNonce, storagePwd, sessionPubKeyB64),
                    t
            );
            assertEquals(200, JsonParsers.status(createResp), "CreateAuthSession(" + label + ") must be 200");

            String sid = JsonParsers.sessionId(createResp);
            assertNotNull(sid, "sessionId must not be null");

            r.ok("Создана сессия " + label + ": sessionId=" + sid);

            // для тестов используем devicePriv как sessionPriv
            byte[] sessionPrivKey = TestConfig.getDevicePrivatKey(login);

            return new Session(sid, sessionPrivKey, storagePwd);
        }
    }

    private static void sessionLogin2Steps(WsSession ws, Session s, Duration t, String label, TestResult r) {
        // шаг 1: SessionChallenge(sessionId)
        String chResp = ws.call("SessionChallenge " + label, JsonBuilders.sessionChallenge(s.sessionId), t);
        assertEquals(200, JsonParsers.status(chResp), "SessionChallenge must be 200");
        String nonce = JsonParsers.sessionNonce(chResp);
        assertNotNull(nonce, "SessionChallenge nonce must not be null");

        // шаг 2: SessionLogin(sessionId, timeMs, signature(sessionKey, SESSION_LOGIN:...))
        String loginResp = ws.call("SessionLogin " + label, JsonBuilders.sessionLogin(s.sessionId, nonce, s.sessionPrivKey), t);
        assertEquals(200, JsonParsers.status(loginResp), "SessionLogin must be 200");

        String storagePwd = JsonParsers.storagePwd(loginResp);
        assertNotNull(storagePwd, "storagePwd must not be null after SessionLogin");
        assertEquals(s.storagePwd, storagePwd, "storagePwd must match what client provided on CreateAuthSession");

        r.ok(label + ": SessionLogin OK, storagePwd verified");
    }

    private record Session(String sessionId, byte[] sessionPrivKey, String storagePwd) {}
}