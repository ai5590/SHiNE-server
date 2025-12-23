package test.it;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class AddUserIT {

    @Test
    void addUser_shouldReturn200_orAlreadyExists() {
        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {

            String reqId = "it-adduser-1";
            String resp = client.request(reqId, JsonBuilders.addUser(reqId), Duration.ofSeconds(5));

            int st = JsonParsers.status(resp);

            // ВАЖНО: тут подставь свой реальный код "уже существует", если он не 200.
            // Я оставляю пример: 409.
            boolean created = (st == 200);
            boolean already = (st == 409);

            if (created) {
                System.out.println("✅ AddUser: создан/добавлен (status=200)");
            } else if (already) {
                System.out.println("✅ AddUser: возможно уже есть в базе (status=409)");
            } else {
                fail("❌ AddUser: неожиданный status=" + st + ", resp=" + resp);
            }
        }
    }
}