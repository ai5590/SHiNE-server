package test.it.cases;

import blockchain.MsgSubType;
import blockchain.body.ConnectionBody;
import blockchain.body.CreateChannelBody;
import blockchain.body.HeaderBody;
import blockchain.body.TextBody;
import test.it.blockchain.AddBlockSender;
import test.it.blockchain.ChainState;
import test.it.utils.TestConfig;
import test.it.utils.log.TestLog;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_03_AddBlock_NoAuth — сценарий блоков (новый формат + каналы + связи).
 *
 * CONNECTION (type=3):
 *  - всегда имеет hasLine (lineCode+prevLineNumber+prevLineHash32+thisLineNumber)
 *  - всегда имеет target:
 *      toBlockchainName + toBlockGlobalNumber + toBlockHash32
 *
 * Правило target для связей/подписок:
 *  - FRIEND/CONTACT -> target = HEADER цели (blockNumber=0)
 *  - FOLLOW пользователя -> target = HEADER цели (blockNumber=0)
 *  - FOLLOW канала -> target = ROOT канала:
 *      канал "0" -> HEADER (0)
 *      канал "X" -> CREATE_CHANNEL (blockNumber create_channel)
 */
public class IT_03_AddBlock_NoAuth {

    public static void main(String[] args) {
        TestLog.info("Standalone: этот тест требует заранее созданных пользователей -> запускаю IT_01_AddUser");
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
                        " USER3=" + u3 + " bch=" + bch3 + "\n" +
                        "\nСценарий: каналы + кросс-чейн reply + connections (follow/friend/contact/uncontact)."
                );
            }

            // =========================
            // USER1
            // =========================
            ChainState st1 = new ChainState();
            AddBlockSender sender1 = new AddBlockSender(ws, st1, u1, bch1, TestConfig.getBlockchainPrivatKey(u1));

            sender1.send(new HeaderBody(u1), t);
            assertTrue(st1.hasHeader());

            int u1HeaderBlock = 0;
            byte[] u1HeaderHash = st1.getHash32(u1HeaderBlock);
            assertNotNull(u1HeaderHash);

            // канал "0" root = HEADER (0)
            int root0 = st1.rootChannel0();

            // POST в канал "0"
            {
                var ln = st1.nextTextLineByRoot(root0);
                sender1.send(new TextBody(
                        MsgSubType.TEXT_POST,
                        root0,
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
                        0, // lineCode TECH
                        ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "News"
                ), t);

                newsRootBlock = st1.lastBlockNumber(); // root канала = blockNumber этого CREATE_CHANNEL
                newsRootHash = st1.getHash32(newsRootBlock);
                assertNotNull(newsRootHash);

                st1.registerTextChannelRoot(newsRootBlock, newsRootHash);
            }

            // POST #0 в канал "News"
            int newsPost0Block;
            byte[] newsPost0Hash;
            {
                var ln = st1.nextTextLineByRoot(newsRootBlock);
                sender1.send(new TextBody(
                        MsgSubType.TEXT_POST,
                        newsRootBlock,
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
                        newsRootBlock,
                        ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "U1: News post #1",
                        null, null, null
                ), t);
            }

            // EDIT_POST (в линии канала) -> target на ОРИГИНАЛЬНЫЙ POST (без toBlockchainName)
            {
                var ln = st1.nextTextLineByRoot(newsRootBlock);
                sender1.send(new TextBody(
                        MsgSubType.TEXT_EDIT_POST,
                        newsRootBlock,
                        ln.prevLineNumber, ln.prevLineHash32, ln.thisLineNumber,
                        "U1: News post #0 (EDIT)",
                        null,
                        newsPost0Block,
                        newsPost0Hash
                ), t);
            }

            // =========================
            // USER2
            // =========================
            ChainState st2 = new ChainState();
            AddBlockSender sender2 = new AddBlockSender(ws, st2, u2, bch2, TestConfig.getBlockchainPrivatKey(u2));

            sender2.send(new HeaderBody(u2), t);
            assertTrue(st2.hasHeader());

            int u2HeaderBlock = 0;
            byte[] u2HeaderHash = st2.getHash32(u2HeaderBlock);
            assertNotNull(u2HeaderHash);

            // =========================
            // СВЯЗИ (CONNECTION)
            // =========================

            // 1) U1 подписался на U2 (FOLLOW на пользователя -> target=HEADER U2)
            sendConnection(sender1, st1, MsgSubType.CONNECTION_FOLLOW,
                    bch2, u2HeaderBlock, u2HeaderHash,
                    "U1 follows U2 (target=U2 HEADER)", t);

            // 2) U2 подписался на канал U1 "News" (FOLLOW на канал -> target=root CREATE_CHANNEL U1)
            sendConnection(sender2, st2, MsgSubType.CONNECTION_FOLLOW,
                    bch1, newsRootBlock, newsRootHash,
                    "U2 follows U1 channel 'News' (target=U1 CREATE_CHANNEL root)", t);

            // 3) FRIEND взаимно (на HEADER)
            sendConnection(sender1, st1, MsgSubType.CONNECTION_FRIEND,
                    bch2, u2HeaderBlock, u2HeaderHash,
                    "U1 -> U2: FRIEND", t);

            sendConnection(sender2, st2, MsgSubType.CONNECTION_FRIEND,
                    bch1, u1HeaderBlock, u1HeaderHash,
                    "U2 -> U1: FRIEND", t);

            // 4) CONTACT несколько
            sendConnection(sender1, st1, MsgSubType.CONNECTION_CONTACT,
                    bch2, u2HeaderBlock, u2HeaderHash,
                    "U1 -> U2: CONTACT", t);

            sendConnection(sender2, st2, MsgSubType.CONNECTION_CONTACT,
                    bch1, u1HeaderBlock, u1HeaderHash,
                    "U2 -> U1: CONTACT", t);

            // =========================
            // USER2 REPLY (ответ в чужой канал)
            // =========================
            {
                sender2.send(TextBody.newReply(
                        bch1,
                        newsPost0Block,
                        newsPost0Hash,
                        "U2: reply to U1 News post #0 (cross-chain)"
                ), t);
            }

            // =========================
            // USER3 + доп. контакт
            // =========================
            ChainState st3 = new ChainState();
            AddBlockSender sender3 = new AddBlockSender(ws, st3, u3, bch3, TestConfig.getBlockchainPrivatKey(u3));

            sender3.send(new HeaderBody(u3), t);
            assertTrue(st3.hasHeader());

            int u3HeaderBlock = 0;
            byte[] u3HeaderHash = st3.getHash32(u3HeaderBlock);
            assertNotNull(u3HeaderHash);

            // U1 -> U3: CONTACT
            sendConnection(sender1, st1, MsgSubType.CONNECTION_CONTACT,
                    bch3, u3HeaderBlock, u3HeaderHash,
                    "U1 -> U3: CONTACT", t);

            // 5) U1 убирает U2 из контактов (UNCONTACT)
            sendConnection(sender1, st1, MsgSubType.CONNECTION_UNCONTACT,
                    bch2, u2HeaderBlock, u2HeaderHash,
                    "U1 -> U2: UNCONTACT", t);

            r.ok("IT_03 сценарий блоков + connections выполнен");

        } catch (Throwable e) {
            r.fail("IT_03 упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    /**
     * Отправка 1 блока CONNECTION.
     *
     * ВАЖНО: ConnectionBody НЕ содержит note в байтах.
     * Если нужно “описание” — логируем отдельно.
     */
    private static void sendConnection(AddBlockSender sender,
                                       ChainState st,
                                       short subType,
                                       String toBlockchainName,
                                       int toBlockNumber,
                                       byte[] toBlockHash32,
                                       String logNote,
                                       Duration timeout) {

        if (TestConfig.DEBUG()) {
            TestLog.info("CONNECTION: subType=" + (subType & 0xFFFF)
                    + " to=" + toBlockchainName
                    + " targetBlock=" + toBlockNumber
                    + " note=" + logNote);
        }

        var ln = st.nextLineByType(ChainState.TYPE_CONNECTION);

        // КОНСТРУКТОР ИЗ ТВОЕГО КОДА:
        // ConnectionBody(int lineCode, int prevLineNumber, byte[] prevLineHash32, int thisLineNumber,
        //                short subType, String toBlockchainName, int toBlockGlobalNumber, byte[] toBlockHash32)
        sender.send(new ConnectionBody(
                0, // lineCode для connection линии
                ln.prevLineNumber,
                ln.prevLineHash32,
                ln.thisLineNumber,
                subType,
                toBlockchainName,
                toBlockNumber,
                toBlockHash32
        ), timeout);
    }
}