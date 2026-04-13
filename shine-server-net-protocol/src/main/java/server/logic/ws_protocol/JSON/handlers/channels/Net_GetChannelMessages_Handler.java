package server.logic.ws_protocol.JSON.handlers.channels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_GetChannelMessages_Request;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_GetChannelMessages_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.SqliteDbController;
import utils.blockchain.BlockchainNameUtil;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class Net_GetChannelMessages_Handler implements JsonMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(Net_GetChannelMessages_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_GetChannelMessages_Request req = (Net_GetChannelMessages_Request) baseRequest;
        if (req.getChannel() == null
                || req.getChannel().getOwnerBlockchainName() == null
                || req.getChannel().getOwnerBlockchainName().isBlank()
                || req.getChannel().getChannelRootBlockNumber() == null) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "bad_fields", "Некорректные поля channel");
        }

        int limit = req.getLimit() == null ? 30 : req.getLimit();
        if (limit <= 0 || limit > 1000) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "limit_too_large", "Некорректный limit");
        }

        boolean asc = req.getSort() == null || !"desc".equalsIgnoreCase(req.getSort());

        try (Connection c = SqliteDbController.getInstance().getConnection()) {
            String viewerLogin = ctx != null ? ctx.getLogin() : null;
            if (viewerLogin == null || viewerLogin.isBlank()) {
                viewerLogin = ChannelsReadSupport.canonicalLogin(c, req.getLogin());
            }
            String ownerBch = req.getChannel().getOwnerBlockchainName();
            int lineCode = req.getChannel().getChannelRootBlockNumber();

            Net_GetChannelMessages_Response resp = new Net_GetChannelMessages_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);

            Net_GetChannelMessages_Response.Channel channel = new Net_GetChannelMessages_Response.Channel();
            channel.setOwnerBlockchainName(ownerBch);
            channel.setOwnerLogin(BlockchainNameUtil.loginFromBlockchainName(ownerBch));
            channel.setChannelName(ChannelsReadSupport.detectChannelName(c, ownerBch, lineCode));
            Net_GetChannelMessages_Response.BlockRef rootRef = new Net_GetChannelMessages_Response.BlockRef();
            rootRef.setBlockNumber(lineCode);
            rootRef.setBlockHash(req.getChannel().getChannelRootBlockHash());
            channel.setChannelRoot(rootRef);
            resp.setChannel(channel);

            List<ChannelsReadSupport.PostBlock> posts = ChannelsReadSupport.channelPosts(c, ownerBch, lineCode, limit, asc);
            List<Net_GetChannelMessages_Response.MessageItem> items = new ArrayList<>();

            for (ChannelsReadSupport.PostBlock post : posts) {
                Net_GetChannelMessages_Response.MessageItem item = new Net_GetChannelMessages_Response.MessageItem();
                Net_GetChannelMessages_Response.BlockRef msgRef = new Net_GetChannelMessages_Response.BlockRef();
                msgRef.setBlockNumber(post.blockNumber);
                msgRef.setBlockHash(ChannelsReadSupport.toHex(post.blockHash));
                item.setMessageRef(msgRef);
                item.setAuthorLogin(post.login);
                item.setAuthorBlockchainName(post.bchName);

                List<Net_GetChannelMessages_Response.VersionItem> versionsOut = new ArrayList<>();
                int index = 1;

                ChannelsReadSupport.TextInfo postText = ChannelsReadSupport.parseTextAndTime(post.blockBytes);
                Net_GetChannelMessages_Response.VersionItem v1 = new Net_GetChannelMessages_Response.VersionItem();
                v1.setVersionIndex(index++);
                v1.setBlockNumber(post.blockNumber);
                v1.setBlockHash(ChannelsReadSupport.toHex(post.blockHash));
                v1.setText(postText.text);
                v1.setCreatedAtMs(postText.createdAtMs);
                versionsOut.add(v1);

                List<ChannelsReadSupport.PostBlock> edits = ChannelsReadSupport.versionsForPost(c, ownerBch, post.blockNumber, post.blockHash);
                for (ChannelsReadSupport.PostBlock edit : edits) {
                    ChannelsReadSupport.TextInfo editText = ChannelsReadSupport.parseTextAndTime(edit.blockBytes);
                    Net_GetChannelMessages_Response.VersionItem ve = new Net_GetChannelMessages_Response.VersionItem();
                    ve.setVersionIndex(index++);
                    ve.setBlockNumber(edit.blockNumber);
                    ve.setBlockHash(ChannelsReadSupport.toHex(edit.blockHash));
                    ve.setText(editText.text);
                    ve.setCreatedAtMs(editText.createdAtMs);
                    versionsOut.add(ve);
                }

                item.setVersions(versionsOut);
                item.setVersionsTotal(versionsOut.size());

                Net_GetChannelMessages_Response.VersionItem lastV = versionsOut.get(versionsOut.size() - 1);
                item.setText(lastV.getText());
                item.setCreatedAtMs(postText.createdAtMs);

                int[] stats = ChannelsReadSupport.loadStats(c, ownerBch, post.blockNumber, post.blockHash);
                item.setLikesCount(stats[0]);
                item.setRepliesCount(stats[1]);
                item.setLikedByMe(ChannelsReadSupport.isLikedByLogin(c, viewerLogin, post.bchName, post.blockNumber, post.blockHash));

                items.add(item);
            }

            resp.setMessages(items);
            return resp;
        } catch (Exception e) {
            log.error("GetChannelMessages failed", e);
            return NetExceptionResponseFactory.error(req, WireCodes.Status.INTERNAL_ERROR, "internal_error", "Внутренняя ошибка сервера");
        }
    }
}
