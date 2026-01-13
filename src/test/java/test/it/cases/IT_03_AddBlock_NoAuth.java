package test.it.cases;

import blockchain.LineIndex;
import blockchain.body.*;
import shine.db.MsgSubType;
import test.it.blockchain.AddBlockSender;
import test.it.blockchain.ChainState;
import test.it.utils.TestConfig;
import test.it.utils.log.TestLog;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_03_AddBlock_NoAuth — обновлён под новый формат блоков (ТЗ).
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
        String u3 = TestConfig.LOGIN3();

        String bch1 = TestConfig.getBlockchainName(u1);
        String bch2 = TestConfig.getBlockchainName(u2);
        String bch3 = TestConfig.getBlockchainName(u3);

        Duration t = Duration.ofSeconds(1);

        try (WsSession ws = WsSession.open()) {

            if (TestConfig.DEBUG()) {
                TestLog.titleBlock(
                        "IT_03:\n" +
                        " USER1=" + u1 + " bch=" + bch1 + "\n" +
                        " USER2=" + u2 + " bch=" + bch2 + "\n" +
                        " USER3=" + u3 + " bch=" + bch3
                );
            }

            // USER1
            ChainState st1 = new ChainState();
            AddBlockSender sender1 = new AddBlockSender(ws, st1, u1, bch1, TestConfig.getBlockchainPrivatKey(u1));

            sender1.send(new HeaderBody(u1), t);
            assertTrue(st1.hasHeader());

            // TEXT_NEW x3 (с line)
            {
                var ln = st1.nextLine(LineIndex.TEXT);
                sender1.send(new TextBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.TEXT_NEW,
                        "Hello #1 (NEW) from IT_03 test",
                        null, null, null
                ), t);
            }
            {
                var ln = st1.nextLine(LineIndex.TEXT);
                sender1.send(new TextBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.TEXT_NEW,
                        "Hello #2 (NEW) from IT_03 test",
                        null, null, null
                ), t);
            }
            {
                var ln = st1.nextLine(LineIndex.TEXT);
                sender1.send(new TextBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.TEXT_NEW,
                        "Hello #3 (NEW) from IT_03 test",
                        null, null, null
                ), t);
            }

            byte[] text1Hash = st1.getHash32(1);
            byte[] text2Hash = st1.getHash32(2);
            byte[] text3Hash = st1.getHash32(3);
            assertNotNull(text1Hash);
            assertNotNull(text2Hash);
            assertNotNull(text3Hash);

            // TEXT_REPLY x2 (с line + target)
            {
                var ln = st1.nextLine(LineIndex.TEXT);
                sender1.send(new TextBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.TEXT_REPLY,
                        "Reply to TEXT#1",
                        bch1, 1, text1Hash
                ), t);
            }
            {
                var ln = st1.nextLine(LineIndex.TEXT);
                sender1.send(new TextBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.TEXT_REPLY,
                        "Reply to TEXT#3",
                        bch1, 3, text3Hash
                ), t);
            }

            // REACTION_LIKE x2 (без line)
            sender1.send(new ReactionBody(bch1, 1, text1Hash), t);
            sender1.send(new ReactionBody(bch1, 2, text2Hash), t);

            // TEXT_EDIT x3 (с line + target)
            {
                var ln = st1.nextLine(LineIndex.TEXT);
                sender1.send(new TextBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.TEXT_EDIT,
                        "Hello #2 (EDIT#1) from IT_03 test",
                        bch1, 2, text2Hash
                ), t);
            }
            {
                var ln = st1.nextLine(LineIndex.TEXT);
                sender1.send(new TextBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.TEXT_EDIT,
                        "Hello #2 (EDIT#2) from IT_03 test",
                        bch1, 2, text2Hash
                ), t);
            }
            {
                var ln = st1.nextLine(LineIndex.TEXT);
                sender1.send(new TextBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.TEXT_EDIT,
                        "Hello #3 (EDIT#1) from IT_03 test",
                        bch1, 3, text3Hash
                ), t);
            }

            assertEquals(10, st1.lastBlockNumber(), "USER1: lastBlockNumber должен быть 10 (всего 11 блоков включая HEADER)");

            // USER2
            ChainState st2 = new ChainState();
            AddBlockSender sender2 = new AddBlockSender(ws, st2, u2, bch2, TestConfig.getBlockchainPrivatKey(u2));

            sender2.send(new HeaderBody(u2), t);
            assertTrue(st2.hasHeader());

            // USER_PARAM (с line)
            {
                var ln = st2.nextLine(LineIndex.USER_PARAM);
                sender2.send(new UserParamBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "Anya", "Amsterdam, Example street 10"
                ), t);
            }

            // USER3 (нужен, чтобы u1 мог подписаться на существующий блокчейн)
            ChainState st3 = new ChainState();
            AddBlockSender sender3 = new AddBlockSender(ws, st3, u3, bch3, TestConfig.getBlockchainPrivatKey(u3));

            sender3.send(new HeaderBody(u3), t);
            assertTrue(st3.hasHeader());

            // -----------------------------------------------------------------
            // Подписки:
            //  - u1 follows u2 и u3
            //  - u2 follows только u1
            // Все CONNECTION идут по линии CONNECTION (по ТЗ "да надо")
            // -----------------------------------------------------------------

            // u1 -> follow u2
            {
                var ln = st1.nextLine(LineIndex.CONNECTION);
                sender1.send(new ConnectionBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.CONNECTION_FOLLOW,
                        u2, bch2, 0, new byte[32]
                ), t);
            }

            // u1 -> follow u3
            {
                var ln = st1.nextLine(LineIndex.CONNECTION);
                sender1.send(new ConnectionBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.CONNECTION_FOLLOW,
                        u3, bch3, 0, new byte[32]
                ), t);
            }

            // u2 -> follow u1
            {
                var ln = st2.nextLine(LineIndex.CONNECTION);
                sender2.send(new ConnectionBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.CONNECTION_FOLLOW,
                        u1, bch1, 0, new byte[32]
                ), t);
            }

            // friend/unfriend как было, но тоже по CONNECTION линии
            {
                var ln = st2.nextLine(LineIndex.CONNECTION);
                sender2.send(new ConnectionBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.CONNECTION_FRIEND,
                        u1, bch1, 0, new byte[32]
                ), t);
            }

            // user1 param + friend to u2
            {
                var ln = st1.nextLine(LineIndex.USER_PARAM);
                sender1.send(new UserParamBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "Anna", "Gareeva"
                ), t);
            }
            {
                var ln = st1.nextLine(LineIndex.CONNECTION);
                sender1.send(new ConnectionBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.CONNECTION_FRIEND,
                        u2, bch2, 0, new byte[32]
                ), t);
            }

            {
                var ln = st2.nextLine(LineIndex.CONNECTION);
                sender2.send(new ConnectionBody(ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        MsgSubType.CONNECTION_UNFRIEND,
                        u1, bch1, 0, new byte[32]
                ), t);
            }

            r.ok("IT_03 сценарий блоков выполнен");

        } catch (Throwable e) {
            r.fail("IT_03 упал: " + e.getMessage());
        }

        return r.summaryLine();
    }
}