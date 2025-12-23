package test.it;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class AddUserIT {

    // ANSI цвета (работает в большинстве терминалов)
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

    @Test
    void addUser_shouldReturn200_orAlreadyExists() {
        title("AddUserIT: проверка добавления пользователя (200 OK) или 'уже существует' (409 USER_ALREADY_EXISTS)");
        System.out.println("Ожидание:");
        System.out.println("  - сервер принимает AddUser и возвращает:");
        System.out.println("      * 200 (пользователь создан/добавлен)");
        System.out.println("      * либо 409 + payload.code=USER_ALREADY_EXISTS (если уже есть)\n");

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {

            String reqId = "it-adduser-1";
            String reqJson = JsonBuilders.addUser(reqId);

            System.out.println("📤 Отправляем AddUser запрос:");
            System.out.println(reqJson);
            line();

            String resp = client.request(reqId, reqJson, Duration.ofSeconds(5));

            System.out.println("📥 Ответ сервера:");
            System.out.println(resp);
            line();

            int st = JsonParsers.status(resp);
            System.out.println("ℹ️ status=" + st);

            boolean created = (st == 200);
            boolean already = (st == 409);

            if (already) {
                // ВАЖНО: payload.code (не errorCode)
                String code = JsonParsers.errorCode(resp);

                System.out.println("ℹ️ server_code=" + code);

                // Если 409 пришёл, требуем понятный code
                try {
                    assertEquals("USER_ALREADY_EXISTS", code,
                            "Expected code=USER_ALREADY_EXISTS, but got: " + code + ", resp=" + resp);
                    ok("409 получен корректно: USER_ALREADY_EXISTS");
                } catch (AssertionError ae) {
                    boom("409 получен, но code не тот. " + ae.getMessage());
                    throw ae;
                }
            }

            if (created) {
                ok("ТЕСТ ПРОЙДЕН: AddUser создан/добавлен (status=200)");
            } else if (already) {
                ok("ТЕСТ ПРОЙДЕН: AddUser уже есть в системе (status=409, USER_ALREADY_EXISTS)");
            } else {
                boom("Неожиданный status=" + st + ", resp=" + resp);
                fail("❌ AddUser: неожиданный status=" + st + ", resp=" + resp);
            }

        } catch (AssertionError | RuntimeException e) {
            // чтобы “красным” было видно даже если Gradle/IDE печатает стек отдельно
            boom("ТЕСТ УПАЛ: AddUserIT. Причина: " + e.getMessage());
            throw e;
        }
    }
}
