package test.it;

import blockchain.body.HeaderBody;
import blockchain.body.ReactionBody;
import blockchain.body.TextBody;
import org.junit.jupiter.api.BeforeAll;
import test.it.addBlockUtils.AddBlockSender;
import test.it.addBlockUtils.ChainState;
import test.it.utils.ItRunContext;
import test.it.utils.TestConfig;
import test.it.utils.TestLog;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_03_AddBlock_NoAuth
 *
 * Теперь тест максимально "линейный":
 *  - создаём только Body
 *  - sender.send(body) делает всё остальное (номера, prev-hash, подпись, отправка, проверка, state)
 *
 * ДОБАВЛЕНО:
 *  - 2 reply на старые сообщения (включая reply на reply)
 *  - ещё 1 реакция (вторая)
 */
public class IT_03_AddBlock_NoAuth {

    public static void main(String[] args) {
        int failed = run();
//        System.exit(failed);
    }

    public static int run() {
        return TestLog.runOne("IT_03_AddBlock_NoAuth", IT_03_AddBlock_NoAuth::testBody);
    }

    @BeforeAll
    static void ensureUserExists() {
        ItRunContext.initIfNeeded();
        // как и было: предусловие можно включить потом
    }

    private static void testBody() {
        ItRunContext.initIfNeeded();
        ensureUserExists();

        Duration t = Duration.ofSeconds(1);

        if (TestConfig.DEBUG()) {
            TestLog.titleBlock("""
                    IT_03_AddBlock_NoAuth: AddBlock без отдельной авторизации (Body-only в тесте)
                    login          = %s
                    blockchainName  = %s
                    """.formatted(TestConfig.LOGIN(), TestConfig.BCH_NAME()));
        }

        ChainState state = new ChainState();
        AddBlockSender sender = new AddBlockSender(state);

        // =========================================================
        // 0) HEADER
        // =========================================================
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 0: HEADER");
        sender.send(new HeaderBody(TestConfig.LOGIN()), t);
        assertTrue(state.hasHeader());

        // =========================================================
        // 1..3) TEXT NEW
        // =========================================================
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 1: TEXT#1 (NEW)");
        sender.send(new TextBody(TextBody.SUB_NEW, "Hello #1 (NEW) from IT_03 test"), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 2: TEXT#2 (NEW)");
        sender.send(new TextBody(TextBody.SUB_NEW, "Hello #2 (NEW) from IT_03 test"), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 3: TEXT#3 (NEW)");
        sender.send(new TextBody(TextBody.SUB_NEW, "Hello #3 (NEW) from IT_03 test"), t);

        // Теперь у нас есть:
        // global=1 -> TEXT#1
        // global=2 -> TEXT#2
        // global=3 -> TEXT#3

        byte[] text1Hash = state.getGlobalHash32(1);
        byte[] text2Hash = state.getGlobalHash32(2);
        assertNotNull(text1Hash);
        assertNotNull(text2Hash);

        // =========================================================
        // 4) REPLY на TEXT#1
        // =========================================================
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 4: TEXT#4 (REPLY -> TEXT#1)");
        sender.send(new TextBody(
                TextBody.SUB_REPLY,
                "Reply to TEXT#1",
                TestConfig.BCH_NAME(),
                1,
                text1Hash
        ), t);

        // global=4 -> REPLY на global=1
        byte[] reply1Hash = state.getGlobalHash32(4);
        assertNotNull(reply1Hash);

        // =========================================================
        // 5) REPLY на REPLY (ответ на ответ)
        // =========================================================
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 5: TEXT#5 (REPLY -> TEXT#4)");
        sender.send(new TextBody(
                TextBody.SUB_REPLY,
                "Reply to REPLY (TEXT#4)",
                TestConfig.BCH_NAME(),
                4,
                reply1Hash
        ), t);

        // =========================================================
        // 6) REACTION#1 -> LIKE на TEXT#1
        // =========================================================
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 6: REACT#1 (LIKE -> TEXT#1)");
        sender.send(new ReactionBody(
                ReactionBody.SUB_LIKE,
                TestConfig.BCH_NAME(),
                1,
                text1Hash
        ), t);

        // =========================================================
        // 7) REACTION#2 -> LIKE на REPLY (TEXT#4)
        // =========================================================
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 7: REACT#2 (LIKE -> TEXT#4)");
        sender.send(new ReactionBody(
                ReactionBody.SUB_LIKE,
                TestConfig.BCH_NAME(),
                4,
                reply1Hash
        ), t);

        // =========================================================
        // Итоги: 1 header + 3 new + 2 reply + 2 react = 8 блоков
        // globalLastNumber должен быть 7
        // =========================================================
        assertEquals(7, state.globalLastNumber(), "Должно быть 8 блоков: globalLastNumber=7");
        assertEquals(5, state.lineLastNumber((short) 1), "В line=1 должно быть 5 TEXT блоков (3 new + 2 reply)");
        assertEquals(2, state.lineLastNumber((short) 2), "В line=2 должно быть 2 REACTION блока");

        assertNotNull(state.globalLastHashHex());
        assertEquals(64, state.globalLastHashHex().length());

        TestLog.pass("IT_03_AddBlock_NoAuth: OK");
    }
}