package server.logic.ws_protocol.JSON.handlers.channels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_GetChannelMessages_Response;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_GetMessageThread_Request;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_GetMessageThread_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.MsgSubType;
import shine.db.SqliteDbController;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class Net_GetMessageThread_Handler implements JsonMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(Net_GetMessageThread_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_GetMessageThread_Request req = (Net_GetMessageThread_Request) baseRequest;
        if (req.getMessage() == null || req.getMessage().getBlockchainName() == null || req.getMessage().getBlockNumber() == null) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "bad_fields", "РќРµРєРѕСЂСЂРµРєС‚РЅС‹Рµ РїРѕР»СЏ message");
        }

        int depthUp = req.getDepthUp() == null ? 20 : Math.max(0, req.getDepthUp());
        int depthDown = req.getDepthDown() == null ? 2 : Math.max(0, req.getDepthDown());
        int childLimit = req.getLimitChildrenPerNode() == null ? 50 : Math.max(1, req.getLimitChildrenPerNode());

        try (Connection c = SqliteDbController.getInstance().getConnection()) {
            String viewerLogin = ctx != null ? ctx.getLogin() : null;
            if (viewerLogin == null || viewerLogin.isBlank()) {
                viewerLogin = ChannelsReadSupport.canonicalLogin(c, req.getLogin());
            }
            PostRow focusRow = findByNumber(c, req.getMessage().getBlockchainName(), req.getMessage().getBlockNumber());
            if (focusRow == null) {
                return NetExceptionResponseFactory.error(req, 404, "message_not_found", "РЎРѕРѕР±С‰РµРЅРёРµ РЅРµ РЅР°Р№РґРµРЅРѕ");
            }

            Net_GetMessageThread_Response resp = new Net_GetMessageThread_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);

            resp.setFocus(toNode(c, focusRow, viewerLogin));

            List<Net_GetMessageThread_Response.MessageNode> ancestors = new ArrayList<>();
            PostRow cur = focusRow;
            for (int i = 0; i < depthUp; i++) {
                if (cur.toBlockNumber == null || cur.toBchName == null) break;
                PostRow parent = findByNumber(c, cur.toBchName, cur.toBlockNumber);
                if (parent == null) break;
                ancestors.add(0, toNode(c, parent, viewerLogin));
                cur = parent;
            }
            resp.setAncestors(ancestors);

            resp.setDescendants(loadChildren(c, focusRow, depthDown, childLimit, viewerLogin));
            return resp;
        } catch (Exception e) {
            log.error("GetMessageThread failed", e);
            return NetExceptionResponseFactory.error(req, WireCodes.Status.INTERNAL_ERROR, "internal_error", "Р’РЅСѓС‚СЂРµРЅРЅСЏСЏ РѕС€РёР±РєР° СЃРµСЂРІРµСЂР°");
        }
    }

    private List<Net_GetMessageThread_Response.MessageNodeTree> loadChildren(Connection c, PostRow parent, int depthDown, int childLimit, String viewerLogin) throws Exception {
        if (depthDown <= 0) return List.of();
        List<PostRow> replies = findReplies(c, parent.bchName, parent.blockNumber, parent.blockHash, childLimit);
        List<Net_GetMessageThread_Response.MessageNodeTree> out = new ArrayList<>();
        for (PostRow row : replies) {
            Net_GetMessageThread_Response.MessageNodeTree t = new Net_GetMessageThread_Response.MessageNodeTree();
            t.setNode(toNode(c, row, viewerLogin));
            t.setChildren(loadChildren(c, row, depthDown - 1, childLimit, viewerLogin));
            out.add(t);
        }
        return out;
    }

    private List<PostRow> findReplies(Connection c, String toBchName, int toBlockNumber, byte[] toBlockHash, int limit) throws Exception {
        String sql = """
            SELECT login,bch_name,block_number,block_hash,block_bytes,to_bch_name,to_block_number,to_block_hash,line_code,msg_sub_type
            FROM blocks
            WHERE msg_type=1 AND msg_sub_type=?
              AND to_bch_name=? AND to_block_number=? AND to_block_hash=?
            ORDER BY block_number ASC
            LIMIT ?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, MsgSubType.TEXT_REPLY);
            ps.setString(2, toBchName);
            ps.setInt(3, toBlockNumber);
            ps.setBytes(4, toBlockHash);
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<PostRow> out = new ArrayList<>();
                while (rs.next()) out.add(mapRow(rs));
                return out;
            }
        }
    }

    private PostRow findByNumber(Connection c, String bchName, int blockNumber) throws Exception {
        String sql = """
            SELECT login,bch_name,block_number,block_hash,block_bytes,to_bch_name,to_block_number,to_block_hash,line_code,msg_sub_type
            FROM blocks
            WHERE bch_name=? AND block_number=?
            LIMIT 1
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bchName);
            ps.setInt(2, blockNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private PostRow mapRow(ResultSet rs) throws Exception {
        PostRow row = new PostRow();
        row.login = rs.getString("login");
        row.bchName = rs.getString("bch_name");
        row.blockNumber = rs.getInt("block_number");
        row.blockHash = rs.getBytes("block_hash");
        row.blockBytes = rs.getBytes("block_bytes");
        row.toBchName = rs.getString("to_bch_name");
        row.toBlockNumber = (Integer) rs.getObject("to_block_number");
        row.toBlockHash = rs.getBytes("to_block_hash");
        row.lineCode = (Integer) rs.getObject("line_code");
        row.msgSubType = rs.getInt("msg_sub_type");
        return row;
    }

    private Net_GetMessageThread_Response.MessageNode toNode(Connection c, PostRow row, String viewerLogin) throws Exception {
        Net_GetMessageThread_Response.MessageNode node = new Net_GetMessageThread_Response.MessageNode();
        Net_GetChannelMessages_Response.BlockRef ref = new Net_GetChannelMessages_Response.BlockRef();
        ref.setBlockNumber(row.blockNumber);
        ref.setBlockHash(ChannelsReadSupport.toHex(row.blockHash));
        node.setMessageRef(ref);
        node.setAuthorLogin(row.login);
        node.setAuthorBlockchainName(row.bchName);

        ChannelsReadSupport.TextInfo base = ChannelsReadSupport.parseTextAndTime(row.blockBytes);
        node.setCreatedAtMs(base.createdAtMs);

        List<Net_GetChannelMessages_Response.VersionItem> versions = new ArrayList<>();
        Net_GetChannelMessages_Response.VersionItem first = new Net_GetChannelMessages_Response.VersionItem();
        first.setVersionIndex(1);
        first.setBlockNumber(row.blockNumber);
        first.setBlockHash(ChannelsReadSupport.toHex(row.blockHash));
        first.setText(base.text);
        first.setCreatedAtMs(base.createdAtMs);
        versions.add(first);

        short editType = row.msgSubType == MsgSubType.TEXT_REPLY ? MsgSubType.TEXT_EDIT_REPLY : MsgSubType.TEXT_EDIT_POST;
        for (PostRow edit : findEdits(c, row.bchName, row.blockNumber, row.blockHash, editType)) {
            ChannelsReadSupport.TextInfo et = ChannelsReadSupport.parseTextAndTime(edit.blockBytes);
            Net_GetChannelMessages_Response.VersionItem v = new Net_GetChannelMessages_Response.VersionItem();
            v.setVersionIndex(versions.size() + 1);
            v.setBlockNumber(edit.blockNumber);
            v.setBlockHash(ChannelsReadSupport.toHex(edit.blockHash));
            v.setText(et.text);
            v.setCreatedAtMs(et.createdAtMs);
            versions.add(v);
        }

        node.setVersions(versions);
        node.setVersionsTotal(versions.size());
        node.setText(versions.get(versions.size() - 1).getText());

        int[] stats = ChannelsReadSupport.loadStats(c, row.bchName, row.blockNumber, row.blockHash);
        node.setLikesCount(stats[0]);
        node.setRepliesCount(stats[1]);
        node.setLikedByMe(ChannelsReadSupport.isLikedByLogin(c, viewerLogin, row.bchName, row.blockNumber, row.blockHash));

        if (row.lineCode != null && row.lineCode >= 0) {
            Net_GetMessageThread_Response.ChannelInfo ci = new Net_GetMessageThread_Response.ChannelInfo();
            ci.setOwnerBlockchainName(row.bchName);
            Net_GetChannelMessages_Response.BlockRef root = new Net_GetChannelMessages_Response.BlockRef();
            root.setBlockNumber(row.lineCode);
            root.setBlockHash(null);
            ci.setChannelRoot(root);
            node.setChannelInfo(ci);
        }

        return node;
    }

    private List<PostRow> findEdits(Connection c, String bch, int targetBlock, byte[] targetHash, int subType) throws Exception {
        String sql = """
            SELECT login,bch_name,block_number,block_hash,block_bytes,to_bch_name,to_block_number,to_block_hash,line_code,msg_sub_type
            FROM blocks
            WHERE bch_name=? AND msg_type=1 AND msg_sub_type=?
              AND to_bch_name=? AND to_block_number=? AND to_block_hash=?
            ORDER BY block_number ASC
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bch);
            ps.setInt(2, subType);
            ps.setString(3, bch);
            ps.setInt(4, targetBlock);
            ps.setBytes(5, targetHash);
            try (ResultSet rs = ps.executeQuery()) {
                List<PostRow> out = new ArrayList<>();
                while (rs.next()) out.add(mapRow(rs));
                return out;
            }
        }
    }

    private static final class PostRow {
        String login;
        String bchName;
        int blockNumber;
        byte[] blockHash;
        byte[] blockBytes;
        String toBchName;
        Integer toBlockNumber;
        byte[] toBlockHash;
        Integer lineCode;
        int msgSubType;
    }
}

