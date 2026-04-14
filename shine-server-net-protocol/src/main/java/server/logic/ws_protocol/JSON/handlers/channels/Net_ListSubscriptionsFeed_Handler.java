package server.logic.ws_protocol.JSON.handlers.channels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_ListSubscriptionsFeed_Request;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_ListSubscriptionsFeed_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.MsgSubType;
import shine.db.SqliteDbController;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class Net_ListSubscriptionsFeed_Handler implements JsonMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(Net_ListSubscriptionsFeed_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_ListSubscriptionsFeed_Request req = (Net_ListSubscriptionsFeed_Request) baseRequest;
        if (req.getLogin() == null || req.getLogin().isBlank()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "bad_fields", "Некорректные поля: login");
        }

        try (Connection c = SqliteDbController.getInstance().getConnection()) {
            String canonicalLogin = ChannelsReadSupport.canonicalLogin(c, req.getLogin().trim());
            if (canonicalLogin == null) {
                return NetExceptionResponseFactory.error(req, 404, "user_not_found", "Пользователь не найден");
            }

            Net_ListSubscriptionsFeed_Response resp = new Net_ListSubscriptionsFeed_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);
            resp.setLogin(canonicalLogin);

            List<ChannelKey> own = loadOwnChannels(c, canonicalLogin);
            List<ChannelKey> followedUsersChannels = loadFollowedChannels(c, canonicalLogin, true);
            List<ChannelKey> followedChannels = loadFollowedChannels(c, canonicalLogin, false);

            resp.setOwnedChannels(buildSummaries(c, own));
            resp.setFollowedUsersChannels(buildSummaries(c, followedUsersChannels));
            resp.setFollowedChannels(buildSummaries(c, followedChannels));

            return resp;
        } catch (Exception e) {
            log.error("ListSubscriptionsFeed failed", e);
            return NetExceptionResponseFactory.error(req, WireCodes.Status.INTERNAL_ERROR, "internal_error", "Внутренняя ошибка сервера");
        }
    }

    private List<Net_ListSubscriptionsFeed_Response.ChannelSummary> buildSummaries(Connection c, List<ChannelKey> keys) throws Exception {
        List<Net_ListSubscriptionsFeed_Response.ChannelSummary> out = new ArrayList<>();
        for (ChannelKey key : keys) {
            Net_ListSubscriptionsFeed_Response.ChannelSummary row = new Net_ListSubscriptionsFeed_Response.ChannelSummary();
            Net_ListSubscriptionsFeed_Response.ChannelRef channelRef = new Net_ListSubscriptionsFeed_Response.ChannelRef();
            channelRef.setOwnerLogin(key.ownerLogin);
            channelRef.setOwnerBlockchainName(key.ownerBch);
            channelRef.setChannelName(ChannelsReadSupport.detectChannelName(c, key.ownerBch, key.rootNumber));
            channelRef.setChannelDescription(ChannelsReadSupport.detectChannelDescription(c, key.ownerBch, key.rootNumber));
            channelRef.setPersonal(key.rootNumber == 0);

            Net_ListSubscriptionsFeed_Response.BlockRef rootRef = new Net_ListSubscriptionsFeed_Response.BlockRef();
            rootRef.setBlockNumber(key.rootNumber);
            rootRef.setBlockHash(ChannelsReadSupport.toHex(key.rootHash));
            channelRef.setChannelRoot(rootRef);

            row.setChannel(channelRef);
            row.setMessagesCount(ChannelsReadSupport.countPosts(c, key.ownerBch, key.rootNumber));

            ChannelsReadSupport.PostBlock lastPost = ChannelsReadSupport.loadLastPost(c, key.ownerBch, key.rootNumber);
            if (lastPost != null) {
                ChannelsReadSupport.PostBlock actual = ChannelsReadSupport.loadLastVersion(c, key.ownerBch, lastPost.blockNumber, lastPost.blockHash);
                if (actual == null) actual = lastPost;

                ChannelsReadSupport.TextInfo textInfo = ChannelsReadSupport.parseTextAndTime(actual.blockBytes);
                Net_ListSubscriptionsFeed_Response.LastMessage lm = new Net_ListSubscriptionsFeed_Response.LastMessage();
                Net_ListSubscriptionsFeed_Response.BlockRef msgRef = new Net_ListSubscriptionsFeed_Response.BlockRef();
                msgRef.setBlockNumber(actual.blockNumber);
                msgRef.setBlockHash(ChannelsReadSupport.toHex(actual.blockHash));
                lm.setMessageRef(msgRef);
                lm.setText(textInfo.text);
                lm.setCreatedAtMs(textInfo.createdAtMs);
                lm.setAuthorLogin(actual.login);
                lm.setAuthorBlockchainName(actual.bchName);
                row.setLastMessage(lm);
            }

            out.add(row);
        }
        return out;
    }

    private List<ChannelKey> loadOwnChannels(Connection c, String canonicalLogin) throws Exception {
        List<ChannelKey> out = new ArrayList<>();
        String bchSql = "SELECT blockchain_name FROM blockchain_state WHERE login=? ORDER BY blockchain_name";
        try (PreparedStatement bchPs = c.prepareStatement(bchSql)) {
            bchPs.setString(1, canonicalLogin);
            try (ResultSet bchRs = bchPs.executeQuery()) {
                while (bchRs.next()) {
                    String bch = bchRs.getString("blockchain_name");
                    out.add(new ChannelKey(canonicalLogin, bch, 0, new byte[32]));

                    String chSql = "SELECT block_number,block_hash FROM blocks WHERE bch_name=? AND msg_type=? AND msg_sub_type=? ORDER BY block_number";
                    try (PreparedStatement chPs = c.prepareStatement(chSql)) {
                        chPs.setString(1, bch);
                        chPs.setInt(2, ChannelsReadSupport.MSG_TYPE_TECH);
                        chPs.setInt(3, 1);
                        try (ResultSet chRs = chPs.executeQuery()) {
                            while (chRs.next()) {
                                out.add(new ChannelKey(canonicalLogin, bch, chRs.getInt("block_number"), chRs.getBytes("block_hash")));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private List<ChannelKey> loadFollowedChannels(Connection c, String canonicalLogin, boolean onlyUserRoots) throws Exception {
        List<ChannelKey> out = new ArrayList<>();
        String sql = """
            SELECT cs.to_login, cs.to_bch_name, COALESCE(cs.to_block_number,0) AS root_number, cs.to_block_hash
            FROM connections_state cs
            WHERE cs.login=? AND cs.rel_type=?
            ORDER BY cs.to_login, cs.to_bch_name, root_number
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, canonicalLogin);
            ps.setInt(2, MsgSubType.CONNECTION_FOLLOW);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int rootNumber = rs.getInt("root_number");
                    if (onlyUserRoots && rootNumber != 0) continue;
                    if (!onlyUserRoots && rootNumber == 0) continue;
                    out.add(new ChannelKey(
                            rs.getString("to_login"),
                            rs.getString("to_bch_name"),
                            rootNumber,
                            rs.getBytes("to_block_hash")
                    ));
                }
            }
        }
        return out;
    }

    private static final class ChannelKey {
        final String ownerLogin;
        final String ownerBch;
        final int rootNumber;
        final byte[] rootHash;

        private ChannelKey(String ownerLogin, String ownerBch, int rootNumber, byte[] rootHash) {
            this.ownerLogin = ownerLogin;
            this.ownerBch = ownerBch;
            this.rootNumber = rootNumber;
            this.rootHash = rootHash;
        }
    }
}
