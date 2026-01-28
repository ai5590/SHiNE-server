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
 *
 * Обновление:
 * - теперь AddUser может вернуть 409 не только USER_ALREADY_EXISTS,
 *   но и BLOCKCHAIN_ALREADY_EXISTS / BLOCKCHAIN_STATE_ALREADY_EXISTS.
 * - дополнительно проверяем GetUser (status=200 всегда).
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
            String resp1 = ws.call("AddUser#USER1", JsonBuilders.addUser(TestConfig.LOGIN()), t);
            checkAddUser200or409(r, resp1);
            checkGetUserMustExist(r, ws, TestConfig.LOGIN(), t);

            r.ok("AddUser USER2: " + TestConfig.LOGIN2());
            String resp2 = ws.call("AddUser#USER2", JsonBuilders.addUser(TestConfig.LOGIN2()), t);
            checkAddUser200or409(r, resp2);
            checkGetUserMustExist(r, ws, TestConfig.LOGIN2(), t);

            r.ok("AddUser USER3: " + TestConfig.LOGIN3());
            String resp3 = ws.call("AddUser#USER3", JsonBuilders.addUser(TestConfig.LOGIN3()), t);
            checkAddUser200or409(r, resp3);
            checkGetUserMustExist(r, ws, TestConfig.LOGIN3(), t);

            // Доп: проверяем case-insensitive поиск
            String mixed = mixCase(TestConfig.LOGIN());
            r.ok("GetUser case-insensitive: запрос=" + mixed + " (должен найти " + TestConfig.LOGIN() + ")");
            checkGetUserMustExist(r, ws, mixed, t);

            // Доп: проверяем "не существует" (но status=200)
            String missing = "NoSuchUser_987654321";
            r.ok("GetUser missing: " + missing);
            checkGetUserMustNotExist(r, ws, missing, t);

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

            // раньше был только USER_ALREADY_EXISTS, теперь добавились ещё варианты
            if ("USER_ALREADY_EXISTS".equals(code)) {
                r.ok("AddUser: status=409 USER_ALREADY_EXISTS (уже был)");
                return;
            }
            if ("BLOCKCHAIN_ALREADY_EXISTS".equals(code)) {
                r.ok("AddUser: status=409 BLOCKCHAIN_ALREADY_EXISTS (blockchainName уже занят)");
                return;
            }
            if ("BLOCKCHAIN_STATE_ALREADY_EXISTS".equals(code)) {
                r.ok("AddUser: status=409 BLOCKCHAIN_STATE_ALREADY_EXISTS (blockchain_state уже есть)");
                return;
            }

            r.fail("AddUser: status=409 но code=" + code + ", resp=" + resp);
            fail("AddUser unexpected 409 code=" + code);
        }
        r.fail("AddUser: неожиданный status=" + st + ", resp=" + resp);
        fail("AddUser unexpected status=" + st);
    }

    private static void checkGetUserMustExist(TestResult r, WsSession ws, String loginQuery, Duration t) {
        String resp = ws.call("GetUser#" + loginQuery, JsonBuilders.getUser(loginQuery), t);

        int st = JsonParsers.status(resp);
        if (st != 200) {
            r.fail("GetUser: ожидали status=200, получили " + st + ", resp=" + resp);
            fail("GetUser unexpected status=" + st);
        }

        Boolean exists = JsonParsers.exists(resp);
        if (exists == null || !exists) {
            r.fail("GetUser: ожидали exists=true, resp=" + resp);
            fail("GetUser expected exists=true");
        }

        // Проверяем, что сервер возвращает данные
        String login = JsonParsers.userLogin(resp);
        String blockchainName = JsonParsers.userBlockchainName(resp);
        String solanaKey = JsonParsers.userSolanaKey(resp);
        String blockchainKey = JsonParsers.userBlockchainKey(resp);
        String deviceKey = JsonParsers.userDeviceKey(resp);

        if (isBlank(login) || isBlank(blockchainName) || isBlank(solanaKey) || isBlank(blockchainKey) || isBlank(deviceKey)) {
            r.fail("GetUser: exists=true, но поля пустые/неполные, resp=" + resp);
            fail("GetUser returned incomplete user data");
        }

        // ВАЖНО:
        // Поиск делается без учета регистра, но login/blockchainName должны вернуться как в БД.
        // Для тех логинов, которые мы создаем в тесте, это ровно TestConfig.LOGIN*().
        // Поэтому если запрос был смешанный регистр — сравниваем не с loginQuery, а с "каноничным" логином из конфига.
        String canonical = canonicalLogin(loginQuery);
        if (canonical != null) {
            if (!login.equals(canonical)) {
                r.fail("GetUser: login должен вернуться как в БД. expected=" + canonical + ", got=" + login + ", resp=" + resp);
                fail("GetUser wrong login case");
            }

            String expectedBch = TestConfig.getBlockchainName(canonical);
            if (!blockchainName.equals(expectedBch)) {
                r.fail("GetUser: blockchainName должен вернуться как в БД. expected=" + expectedBch + ", got=" + blockchainName + ", resp=" + resp);
                fail("GetUser wrong blockchainName");
            }

            // ключи должны совпадать с теми, что AddUser использует при регистрации
            String expSol = TestConfig.solanaPublicKeyB64(canonical);
            String expBchKey = TestConfig.blockchainPublicKeyB64(canonical);
            String expDev = TestConfig.devicePublicKeyB64(canonical);

            if (!solanaKey.equals(expSol)) {
                r.fail("GetUser: solanaKey mismatch, resp=" + resp);
                fail("GetUser solanaKey mismatch");
            }
            if (!blockchainKey.equals(expBchKey)) {
                r.fail("GetUser: blockchainKey mismatch, resp=" + resp);
                fail("GetUser blockchainKey mismatch");
            }
            if (!deviceKey.equals(expDev)) {
                r.fail("GetUser: deviceKey mismatch, resp=" + resp);
                fail("GetUser deviceKey mismatch");
            }
        }

        r.ok("GetUser: exists=true, login=" + login + ", blockchainName=" + blockchainName);
    }

    private static void checkGetUserMustNotExist(TestResult r, WsSession ws, String loginQuery, Duration t) {
        String resp = ws.call("GetUser#" + loginQuery, JsonBuilders.getUser(loginQuery), t);

        int st = JsonParsers.status(resp);
        if (st != 200) {
            r.fail("GetUser(not exist): ожидали status=200, получили " + st + ", resp=" + resp);
            fail("GetUser(not exist) unexpected status=" + st);
        }

        Boolean exists = JsonParsers.exists(resp);
        if (exists == null) {
            r.fail("GetUser(not exist): payload.exists отсутствует, resp=" + resp);
            fail("GetUser(not exist) missing exists");
        }
        if (exists) {
            r.fail("GetUser(not exist): ожидали exists=false, resp=" + resp);
            fail("GetUser(not exist) expected exists=false");
        }

        r.ok("GetUser: exists=false (ok)");
    }

    private static String canonicalLogin(String anyCaseLogin) {
        if (anyCaseLogin == null) return null;
        String x = anyCaseLogin.trim();
        if (x.isEmpty()) return null;

        // Привязка только к нашим тестовым логинам, чтобы не гадать.
        if (x.equalsIgnoreCase(TestConfig.LOGIN())) return TestConfig.LOGIN();
        if (x.equalsIgnoreCase(TestConfig.LOGIN2())) return TestConfig.LOGIN2();
        if (x.equalsIgnoreCase(TestConfig.LOGIN3())) return TestConfig.LOGIN3();

        return null;
    }

    private static String mixCase(String s) {
        if (s == null) return null;
        String x = s.trim();
        if (x.length() < 2) return x;
        // простой "микс" без рандома, чтобы тест был детерминированный
        return Character.toUpperCase(x.charAt(0)) + x.substring(1).toLowerCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}