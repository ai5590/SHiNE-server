package test.it;

import blockchain.body.HeaderBody;
import blockchain.body.ReactionBody;
import blockchain.body.TextBody;
import test.it.addBlockUtils.AddBlockSender;
import test.it.addBlockUtils.ChainState;
import test.it.utils.ItRunContext;
import test.it.utils.TestConfig;
import test.it.utils.TestLog;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class IT_03_AddBlock_NoAuth {

    public static void main(String[] args) {
        int failed = run();
//        System.exit(failed);
    }

    public static int run() {
        return TestLog.runOne("IT_03_AddBlock_NoAuth", IT_03_AddBlock_NoAuth::testBody);
    }

    private static void testBody() {
        ItRunContext.initIfNeeded();

        Duration t = Duration.ofSeconds(1);

        if (TestConfig.DEBUG()) {
            TestLog.titleBlock("""
                    IT_03_AddBlock_NoAuth: AddBlock без отдельной авторизации (линейный сценарий)
                    login          = %s
                    blockchainName = %s
                    """.formatted(TestConfig.LOGIN(), TestConfig.BCH_NAME()));
        }

        ChainState st = new ChainState();
        AddBlockSender sender = new AddBlockSender(st);

        // 0) HEADER
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 0: HEADER");
        sender.send(new HeaderBody(TestConfig.LOGIN()), t);

        // 1) TEXT#1
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 1: TEXT#1");
        sender.send(new TextBody("Hello #1 from IT_03 test"), t);

        // Снимок hash TEXT#1 (после отправки)
        // TEXT линия = 1, первый text имеет lineNum=1 и на этом шаге он “последний”.
        byte[] text1Hash32 = st.lineLastHash32((short) 1);
        assertNotNull(text1Hash32);
        assertEquals(32, text1Hash32.length);

        // 2) TEXT#2
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 2: TEXT#2");
        sender.send(new TextBody("Hello #2 from IT_03 test"), t);

        // 3) TEXT#3
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 3: TEXT#3");
        sender.send(new TextBody("Hello #3 from IT_03 test"), t);

        // 4) REACT#1 -> на TEXT#1 (global=1)
        if (TestConfig.DEBUG()) TestLog.stepTitle("ШАГ 4: REACT#1 -> на TEXT#1");

        ReactionBody like = new ReactionBody(
                ReactionBody.SUB_LIKE,
                TestConfig.BCH_NAME(),
                1,
                text1Hash32
        );
        sender.send(like, t);

        // Итоги (быстро)
        assertEquals(4, st.globalLastNumber(), "После 1 header + 3 text + 1 react globalLastNumber должен быть 4");
        assertEquals(3, st.lineLastNumber((short) 1), "В line=1 должно быть 3 блока");
        assertEquals(1, st.lineLastNumber((short) 2), "В line=2 должен быть 1 блок");

        TestLog.pass("IT_03_AddBlock_NoAuth: OK");
    }
}