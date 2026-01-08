package test.it.cases;

import test.it.utils.TestConfig;
import test.it.utils.json.JsonBuilders;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * IT_01_AddUser
 * Создаёт 3 пользователей: TestUser1/2/3 (200 OK или 409 USER_ALREADY_EXISTS).
 */
public class IT_01_AddUser {

    public static void main(String[] args) {
        String summary = run();
        System.out.println(summary);
    }

    public static String run() {
        TestResult r = new TestResult("IT_01_AddUser");

        Duration t = Duration.ofSeconds(5);

        try (WsSession ws = WsSession.open()) {
            r.ok("AddUser USER1: " + TestConfig.LOGIN());
            checkAddUser200or409(r, ws.call("AddUser#USER1", JsonBuilders.addUser(TestConfig.LOGIN()), t));

            r.ok("AddUser USER2: " + TestConfig.LOGIN2());
            checkAddUser200or409(r, ws.call("AddUser#USER2", JsonBuilders.addUser(TestConfig.LOGIN2()), t));

            r.ok("AddUser USER3: " + TestConfig.LOGIN3());
            checkAddUser200or409(r, ws.call("AddUser#USER3", JsonBuilders.addUser(TestConfig.LOGIN3()), t));
        } catch (Throwable e) {
            r.fail("IT_01_AddUser упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    private static void checkAddUser200or409(TestResult r, String resp) {
        int st = JsonParsers.status(resp);
        if (st == 200) {
            r.ok("AddUser: status=200 (создан)");
            return;
        }
        if (st == 409) {
            String code = JsonParsers.errorCode(resp);
            if ("USER_ALREADY_EXISTS".equals(code)) {
                r.ok("AddUser: status=409 USER_ALREADY_EXISTS (уже был)");
                return;
            }
            r.fail("AddUser: status=409 но code=" + code + ", resp=" + resp);
            fail("AddUser unexpected 409 code=" + code);
        }
        r.fail("AddUser: неожиданный status=" + st + ", resp=" + resp);
        fail("AddUser unexpected status=" + st);
    }
}