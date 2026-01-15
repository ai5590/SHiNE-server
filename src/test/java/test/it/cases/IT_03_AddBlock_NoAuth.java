package test.it.cases;

import blockchain.MsgSubType;
import blockchain.body.*;
import test.it.blockchain.AddBlockSender;
import test.it.blockchain.ChainState;
import test.it.utils.TestConfig;
import test.it.utils.log.TestLog;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_03_AddBlock_NoAuth — сценарий блоков (новый формат + каналы).
 *
 * ВАЖНО:
 *  - TECH: Header + CreateChannel идут по тех-линии (hasLine у CreateChannel).
 *  - TEXT: посты в каналах — отдельные линии, root = Header(канал "0") или CreateChannel(канал "X").
 *  - REPLY (subType=20): без линии, target может указывать на чужой блокчейн, и ОБЯЗАТЕЛЬНО содержит toBlockNumber+toBlockHash32.
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

            // =========================
            // USER1
            // =========================
            ChainState st1 = new ChainState();
            AddBlockSender sender1 = new AddBlockSender(ws, st1, u1, bch1, TestConfig.getBlockchainPrivatKey(u1));

            sender1.send(new HeaderBody(u1), t);
            assertTrue(st1.hasHeader());

            // канал "0" (root=HEADER) — по умолчанию существует
            int root0 = st1.rootChannel0();

            // POST в канал "0"
            {
                var ln = st1.nextTextLineByRoot(root0);
                sender1.send(new TextBody(
                        MsgSubType.TEXT_POST,
                        ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "U1: story/post in channel 0",
                        null, null, null
                ), t);
            }

            int post0Block = st1.lastBlockNumber();
            byte[] post0Hash = st1.getHash32(post0Block);
            assertNotNull(post0Hash);

            // CREATE_CHANNEL "News" (TECH line)
            int newsRootBlock;
            byte[] newsRootHash;
            {
                var ln = st1.nextLineByType(ChainState.TYPE_TECH);
                sender1.send(new CreateChannelBody(
                        ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "News"
                ), t);

                newsRootBlock = st1.lastBlockNumber();
                newsRootHash = st1.getHash32(newsRootBlock);
                assertNotNull(newsRootHash);

                // зарегистрируем root канала для тестового state, чтобы nextTextLineByRoot() работал
                st1.registerTextChannelRoot(newsRootBlock, newsRootHash);
            }

            // POST #0 в канал "News"
            int newsPost0Block;
            byte[] newsPost0Hash;
            {
                var ln = st1.nextTextLineByRoot(newsRootBlock);
                sender1.send(new TextBody(
                        MsgSubType.TEXT_POST,
                        ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "U1: News post #0",
                        null, null, null
                ), t);

                newsPost0Block = st1.lastBlockNumber();
                newsPost0Hash = st1.getHash32(newsPost0Block);
                assertNotNull(newsPost0Hash);
            }

            // POST #1 в канал "News"
            {
                var ln = st1.nextTextLineByRoot(newsRootBlock);
                sender1.send(new TextBody(
                        MsgSubType.TEXT_POST,
                        ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "U1: News post #1",
                        null, null, null
                ), t);
            }

            // EDIT_POST (не увеличивает thisLineNumber, но является частью линии)
            {
                var ln = st1.nextTextLineByRoot(newsRootBlock);
                // edit должен иметь thisLineNumber как у предыдущего сообщения линии (ChainState это уже даёт)
                sender1.send(new TextBody(
                        MsgSubType.TEXT_EDIT_POST,
                        ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "U1: News post #0 (EDIT)",
                        null,
                        newsPost0Block,
                        newsPost0Hash
                ), t);
            }

            // =========================
            // USER2 (ответ в чужой канал)
            // =========================
            ChainState st2 = new ChainState();
            AddBlockSender sender2 = new AddBlockSender(ws, st2, u2, bch2, TestConfig.getBlockchainPrivatKey(u2));

            sender2.send(new HeaderBody(u2), t);
            assertTrue(st2.hasHeader());

            // REPLY (20): ответ на post в чужом блокчейне/канале
            {
                sender2.send(new TextBody(
                        MsgSubType.TEXT_REPLY,
                        -1, new byte[32], -1, // для replies линии нет
                        "U2: reply to U1 News post #0 (cross-chain)",
                        bch1,
                        newsPost0Block,
                        newsPost0Hash
                ), t);
            }

            // =========================
            // USER3 (просто чтобы оставалось как раньше)
            // =========================
            ChainState st3 = new ChainState();
            AddBlockSender sender3 = new AddBlockSender(ws, st3, u3, bch3, TestConfig.getBlockchainPrivatKey(u3));

            sender3.send(new HeaderBody(u3), t);
            assertTrue(st3.hasHeader());

            r.ok("IT_03 сценарий блоков выполнен");

        } catch (Throwable e) {
            r.fail("IT_03 упал: " + e.getMessage());
        }

        return r.summaryLine();
    }
}