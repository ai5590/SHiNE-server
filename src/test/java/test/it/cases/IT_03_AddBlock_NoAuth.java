package test.it.cases;

import blockchain.body.ConnectionBody;
import blockchain.body.HeaderBody;
import blockchain.body.ReactionBody;
import blockchain.body.TextBody;
import blockchain.body.UserParamBody;
import test.it.blockchain.AddBlockSender;
import test.it.blockchain.ChainState;
import test.it.utils.TestConfig;
import test.it.utils.log.TestLog;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_03_AddBlock_NoAuth
 *
 * ВАЖНО:
 *  - пользователей НЕ создаём (их создаёт IT_01)
 *  - ключи берём только из TestConfig по login
 */
public class IT_03_AddBlock_NoAuth {

    public static void main(String[] args) {
        TestLog.info("Standalone: этот тест требует заранее созданных пользователей -> сначала запускаю IT_01_AddUser");
        System.out.println(IT_01_AddUser.run());
        String summary = run();
        System.out.println(summary);
    }

    public static String run() {
        TestResult r = new TestResult("IT_03_AddBlock_NoAuth");

        String u1 = TestConfig.LOGIN();
        String u2 = TestConfig.LOGIN2();

        String bch1 = TestConfig.getBlockchainName(u1);
        String bch2 = TestConfig.getBlockchainName(u2);

        Duration t = Duration.ofSeconds(1);

        try (WsSession ws = WsSession.open()) {

            if (TestConfig.DEBUG()) TestLog.titleBlock("IT_03: USER1=" + u1 + " bch=" + bch1 + " | USER2=" + u2 + " bch=" + bch2);

            // USER1
            ChainState st1 = new ChainState();
            AddBlockSender sender1 = new AddBlockSender(ws, st1, u1, bch1, TestConfig.getBlockchainPrivatKey(u1));

            sender1.send(new HeaderBody(u1), t);
            assertTrue(st1.hasHeader());

            sender1.send(new TextBody(TextBody.SUB_NEW, "Hello #1 (NEW) from IT_03 test"), t);
            sender1.send(new TextBody(TextBody.SUB_NEW, "Hello #2 (NEW) from IT_03 test"), t);
            sender1.send(new TextBody(TextBody.SUB_NEW, "Hello #3 (NEW) from IT_03 test"), t);

            byte[] text1Hash = st1.getGlobalHash32(1);
            byte[] text2Hash = st1.getGlobalHash32(2);
            byte[] text3Hash = st1.getGlobalHash32(3);
            assertNotNull(text1Hash);
            assertNotNull(text2Hash);
            assertNotNull(text3Hash);

            sender1.send(new TextBody(TextBody.SUB_REPLY, "Reply to TEXT#1", bch1, 1, text1Hash), t);
            sender1.send(new TextBody(TextBody.SUB_REPLY, "Reply to TEXT#3", bch1, 3, text3Hash), t);

            sender1.send(new ReactionBody(ReactionBody.SUB_LIKE, bch1, 1, text1Hash), t);
            sender1.send(new ReactionBody(ReactionBody.SUB_LIKE, bch1, 2, text2Hash), t);

            sender1.send(new TextBody(TextBody.SUB_EDIT, "Hello #2 (EDIT#1) from IT_03 test", bch1, 2, text2Hash), t);
            sender1.send(new TextBody(TextBody.SUB_EDIT, "Hello #2 (EDIT#2) from IT_03 test", bch1, 2, text2Hash), t);
            sender1.send(new TextBody(TextBody.SUB_EDIT, "Hello #3 (EDIT#1) from IT_03 test", bch1, 3, text3Hash), t);

            assertEquals(10, st1.globalLastNumber(), "USER1: globalLastNumber должен быть 10 (11 блоков)");
            assertEquals(8, st1.lineLastNumber((short) 1), "USER1: line=1 должно быть 8 TEXT блоков");
            assertEquals(2, st1.lineLastNumber((short) 2), "USER1: line=2 должно быть 2 REACTION блока");

            // USER2
            ChainState st2 = new ChainState();
            AddBlockSender sender2 = new AddBlockSender(ws, st2, u2, bch2, TestConfig.getBlockchainPrivatKey(u2));

            sender2.send(new HeaderBody(u2), t);
            assertTrue(st2.hasHeader());

            sender2.send(new UserParamBody("Anya", "Amsterdam, Example street 10"), t);

            sender2.send(new ConnectionBody(ConnectionBody.SUB_FRIEND, u1, bch1, 0, new byte[32]), t);

            sender1.send(new UserParamBody("Anna", "Gareeva"), t);
            sender1.send(new ConnectionBody(ConnectionBody.SUB_FRIEND, u2, bch2, 0, new byte[32]), t);
            sender1.send(new ConnectionBody(ConnectionBody.SUB_FOLLOW, u2, bch2, 0, new byte[32]), t);

            sender2.send(new ConnectionBody(ConnectionBody.SUB_UNFRIEND, u1, bch1, 0, new byte[32]), t);

            r.ok("IT_03 сценарий блоков выполнен");

        } catch (Throwable e) {
            r.fail("IT_03 упал: " + e.getMessage());
        }

        return r.summaryLine();
    }
}