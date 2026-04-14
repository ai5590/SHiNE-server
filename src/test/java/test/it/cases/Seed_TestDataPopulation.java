package test.it.cases;

import blockchain.MsgSubType;
import blockchain.body.ConnectionBody;
import blockchain.body.HeaderBody;
import test.it.blockchain.AddBlockSender;
import test.it.blockchain.ChainState;
import test.it.utils.TestIds;
import test.it.utils.json.JsonBuilders;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;
import utils.crypto.Ed25519Util;
import utils.crypto.HashSHA256Util;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Seed_TestDataPopulation
 *
 * ВАЖНО:
 * - НЕ заполняет БД напрямую.
 * - Создаёт тестовых пользователей 1..9 через API AddUser.
 * - Создаёт сеть дружбы через API AddBlock (CONNECTION_FRIEND).
 */
public class Seed_TestDataPopulation {

    private static final String PASSWORD = "1";

    public static void main(String[] args) {
        System.out.println(run());
    }

    public static String run() {
        TestResult r = new TestResult("Seed_TestDataPopulation");
        Duration t = Duration.ofSeconds(5);

        try (WsSession ws = WsSession.open()) {
            UserKeys keys = deriveKeysFromPassword(PASSWORD);

            List<String> users = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");

            for (String login : users) {
                createUserViaApi(ws, r, login, keys, t);
            }

            Map<String, ChainState> states = new HashMap<>();
            Map<String, AddBlockSender> senders = new HashMap<>();
            Map<String, byte[]> headerHashes = new HashMap<>();

            for (String login : users) {
                ChainState state = new ChainState();
                AddBlockSender sender = new AddBlockSender(ws, state, login, bch(login), keys.blockchainPrivate32);
                sender.send(new HeaderBody(login), t);
                byte[] headerHash = state.getHash32(0);
                if (headerHash == null) {
                    r.fail("Не удалось получить hash HEADER для " + login);
                    fail("Header hash missing for " + login);
                }
                states.put(login, state);
                senders.put(login, sender);
                headerHashes.put(login, headerHash);
            }

            // Насыщенная сеть дружбы (взаимно). Целевые контрольные значения:
            // 1=5 друзей, 2=7 друзей (<8), 3=3 друга.
            addMutualFriend(senders, states, headerHashes, "1", "2", t);
            addMutualFriend(senders, states, headerHashes, "1", "3", t);
            addMutualFriend(senders, states, headerHashes, "1", "4", t);
            addMutualFriend(senders, states, headerHashes, "1", "5", t);
            addMutualFriend(senders, states, headerHashes, "1", "6", t);

            addMutualFriend(senders, states, headerHashes, "2", "3", t);
            addMutualFriend(senders, states, headerHashes, "2", "4", t);
            addMutualFriend(senders, states, headerHashes, "2", "5", t);
            addMutualFriend(senders, states, headerHashes, "2", "6", t);
            addMutualFriend(senders, states, headerHashes, "2", "7", t);
            addMutualFriend(senders, states, headerHashes, "2", "8", t);

            addMutualFriend(senders, states, headerHashes, "3", "4", t);
            addMutualFriend(senders, states, headerHashes, "4", "5", t);
            addMutualFriend(senders, states, headerHashes, "5", "6", t);
            addMutualFriend(senders, states, headerHashes, "5", "7", t);
            addMutualFriend(senders, states, headerHashes, "6", "8", t);
            addMutualFriend(senders, states, headerHashes, "6", "9", t);
            addMutualFriend(senders, states, headerHashes, "8", "9", t);

            // Контакты: 1/2/3 должны быть друг у друга в контактах (взаимно).
            addMutualContact(senders, states, headerHashes, "1", "2", t);
            addMutualContact(senders, states, headerHashes, "1", "3", t);
            addMutualContact(senders, states, headerHashes, "2", "3", t);

            verifyOutFriendsCount(ws, r, "1", 5, t);
            verifyOutFriendsCount(ws, r, "2", 7, t);
            verifyOutFriendsCount(ws, r, "3", 3, t);

            r.ok("Пользователи 1..9 созданы через API, дружеские и контактные связи созданы через AddBlock");
        } catch (Throwable e) {
            r.fail("Ошибка IT_07: " + e.getMessage());
            fail("IT_07 failed", e);
        }

        return r.summaryLine();
    }

    private static void createUserViaApi(WsSession ws, TestResult r, String login, UserKeys keys, Duration t) {
        String requestId = TestIds.next("adduser_a");
        String req = """
                {
                  "op": "AddUser",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s",
                    "blockchainName": "%s",
                    "solanaKey": "%s",
                    "blockchainKey": "%s",
                    "deviceKey": "%s",
                    "bchLimit": 50000000
                  }
                }
                """.formatted(
                requestId,
                login,
                bch(login),
                keys.solanaPublicB64,
                keys.blockchainPublicB64,
                keys.devicePublicB64
        );

        String resp = ws.call("AddUser#" + login, req, t);
        int st = JsonParsers.status(resp);

        if (st == 200) {
            r.ok("AddUser " + login + ": created");
            return;
        }

        if (st == 409) {
            String code = JsonParsers.errorCode(resp);
            if ("USER_ALREADY_EXISTS".equals(code)
                    || "BLOCKCHAIN_ALREADY_EXISTS".equals(code)
                    || "BLOCKCHAIN_STATE_ALREADY_EXISTS".equals(code)) {
                r.ok("AddUser " + login + ": already exists (" + code + ")");
                return;
            }
        }

        r.fail("AddUser " + login + " unexpected status=" + st + ", resp=" + resp);
        fail("AddUser failed for " + login);
    }

    private static void sendFriendConnection(AddBlockSender sender,
                                             ChainState st,
                                             String targetBch,
                                             byte[] targetHeaderHash,
                                             Duration timeout) {
        sendConnection(sender, st, MsgSubType.CONNECTION_FRIEND, targetBch, targetHeaderHash, timeout);
    }

    private static void sendContactConnection(AddBlockSender sender,
                                              ChainState st,
                                              String targetBch,
                                              byte[] targetHeaderHash,
                                              Duration timeout) {
        sendConnection(sender, st, MsgSubType.CONNECTION_CONTACT, targetBch, targetHeaderHash, timeout);
    }

    private static void sendConnection(AddBlockSender sender,
                                       ChainState st,
                                       short relationSubType,
                                       String targetBch,
                                       byte[] targetHeaderHash,
                                       Duration timeout) {
        ChainState.NextLine ln = st.nextLineByType(ChainState.TYPE_CONNECTION);
        sender.send(new ConnectionBody(
                0,
                ln.prevLineNumber,
                ln.prevLineHash32,
                ln.thisLineNumber,
                relationSubType,
                targetBch,
                0,
                targetHeaderHash
        ), timeout);
    }

    private static void addMutualFriend(Map<String, AddBlockSender> senders,
                                        Map<String, ChainState> states,
                                        Map<String, byte[]> headerHashes,
                                        String a,
                                        String b,
                                        Duration t) {
        sendFriendConnection(senders.get(a), states.get(a), bch(b), headerHashes.get(b), t);
        sendFriendConnection(senders.get(b), states.get(b), bch(a), headerHashes.get(a), t);
    }

    private static void addMutualContact(Map<String, AddBlockSender> senders,
                                         Map<String, ChainState> states,
                                         Map<String, byte[]> headerHashes,
                                         String a,
                                         String b,
                                         Duration t) {
        sendContactConnection(senders.get(a), states.get(a), bch(b), headerHashes.get(b), t);
        sendContactConnection(senders.get(b), states.get(b), bch(a), headerHashes.get(a), t);
    }

    private static void verifyOutFriendsCount(WsSession ws, TestResult r, String login, int expectedCount, Duration t) {
        String resp = ws.call("GetFriendsLists#" + login, JsonBuilders.getFriendsLists(login), t);
        int st = JsonParsers.status(resp);
        if (st != 200) {
            r.fail("GetFriendsLists " + login + " status=" + st + ", resp=" + resp);
            fail("GetFriendsLists failed for " + login);
        }

        List<String> out = JsonParsers.friendsOut(resp);
        if (out.size() != expectedCount) {
            r.fail("У " + login + " ожидалось out_friends=" + expectedCount + ", фактически=" + out.size() + ", resp=" + resp);
            fail("Unexpected friends count for " + login);
        }

        r.ok("GetFriendsLists " + login + ": out_friends=" + out.size());
    }

    private static String bch(String login) {
        return login + "-001";
    }

    private static UserKeys deriveKeysFromPassword(String password) {
        byte[] base = HashSHA256Util.sha256(password.getBytes(StandardCharsets.UTF_8));
        String baseB64 = Base64.getEncoder().encodeToString(base);

        byte[] rootPriv = HashSHA256Util.sha256((baseB64 + "root.key").getBytes(StandardCharsets.UTF_8));
        byte[] bchPriv = HashSHA256Util.sha256((baseB64 + "bch.key").getBytes(StandardCharsets.UTF_8));
        byte[] devPriv = HashSHA256Util.sha256((baseB64 + "dev.key").getBytes(StandardCharsets.UTF_8));

        String rootPubB64 = Base64.getEncoder().encodeToString(Ed25519Util.derivePublicKey(rootPriv));
        String bchPubB64 = Base64.getEncoder().encodeToString(Ed25519Util.derivePublicKey(bchPriv));
        String devPubB64 = Base64.getEncoder().encodeToString(Ed25519Util.derivePublicKey(devPriv));

        return new UserKeys(rootPubB64, bchPubB64, devPubB64, bchPriv);
    }

    private static final class UserKeys {
        final String solanaPublicB64;
        final String blockchainPublicB64;
        final String devicePublicB64;
        final byte[] blockchainPrivate32;

        private UserKeys(String solanaPublicB64,
                         String blockchainPublicB64,
                         String devicePublicB64,
                         byte[] blockchainPrivate32) {
            this.solanaPublicB64 = solanaPublicB64;
            this.blockchainPublicB64 = blockchainPublicB64;
            this.devicePublicB64 = devicePublicB64;
            this.blockchainPrivate32 = blockchainPrivate32;
        }
    }
}
