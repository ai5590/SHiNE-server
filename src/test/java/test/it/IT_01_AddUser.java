package test.it;

import org.junit.jupiter.api.Test;
import test.it.utils.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_01_AddUser
 *
 * Можно запускать:
 *  1) как JUnit тест (через Suite или выборочно)
 *  2) вручную как standalone:
 *     - main()
 *     - или через IT_RunAllMain / IT_RunAllCleanMain
 *
 * Главная цель:
 *  - иметь метод run() -> возвращает число не пройденных тестов (0 или 1)
 *  - и иметь main() для запуска одного теста
 */
public class IT_01_AddUser {

    public static void main(String[] args) {
        // чтобы тест можно было запускать вообще без JUnit
        int failed = run();
//        System.exit(failed);
    }

    /** Запуск одного теста (standalone). Возвращает 0 если ок, 1 если упал. */
    public static int run() {
        return TestLog.runOne("IT_01_AddUser", IT_01_AddUser::testBody);
    }

//    @Test
    void addUser_shouldReturn200_orAlreadyExists() {
        // JUnit-режим: пусть падает через assert/fail как обычно
        testBody();
    }

    private static void testBody() {
        ItRunContext.initIfNeeded();

        TestLog.title("AddUserIT: проверка добавления пользователя (200 OK) или 'уже существует' (409 USER_ALREADY_EXISTS)");
        TestLog.info("Используем:");
        TestLog.info("  login          = " + TestConfig.LOGIN());
        TestLog.info("  blockchainName = " + TestConfig.BCH_NAME());
        TestLog.info("Ожидание:");
        TestLog.info("  - 200 (создан)");
        TestLog.info("  - или 409 + payload.code=USER_ALREADY_EXISTS\n");

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {

            String reqId = "it-adduser-1";
            String reqJson = JsonBuilders.addUser(reqId);

            TestLog.info("📤 Отправляем AddUser запрос:");
            TestLog.info(reqJson);
            TestLog.line();

            String resp = client.request(reqId, reqJson, Duration.ofSeconds(5));

            TestLog.info("📥 Ответ сервера:");
            TestLog.info(resp);
            TestLog.line();

            int st = JsonParsers.status(resp);
            TestLog.info("ℹ️ status=" + st);

            boolean created = (st == 200);
            boolean already = (st == 409);

            if (already) {
                String code = JsonParsers.errorCode(resp);
                TestLog.info("ℹ️ server_code=" + code);

                assertEquals("USER_ALREADY_EXISTS", code,
                        "Expected code=USER_ALREADY_EXISTS, but got: " + code + ", resp=" + resp);

                TestLog.ok("409 получен корректно: USER_ALREADY_EXISTS");
            }

            if (created) {
                TestLog.ok("ТЕСТ ПРОЙДЕН: AddUser создан/добавлен (status=200)");
            } else if (already) {
                TestLog.ok("ТЕСТ ПРОЙДЕН: AddUser уже есть в системе (status=409, USER_ALREADY_EXISTS)");
            } else {
                TestLog.boom("Неожиданный status=" + st + ", resp=" + resp);
                fail("❌ AddUser: неожиданный status=" + st + ", resp=" + resp);
            }

        }
    }
}