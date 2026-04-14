package server.logic.ws_protocol.JSON.handlers.channels;

import blockchain.BchBlockEntry;
import blockchain.body.BodyRecord;
import blockchain.body.CreateChannelBody;
import blockchain.body.TextBody;
import blockchain.body.TextLineBody;
import blockchain.body.TextReplyBody;
import shine.db.MsgSubType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class ChannelsReadSupport {
    static final int MSG_TYPE_TEXT = 1;
    static final int MSG_TYPE_REACTION = 2;
    static final int MSG_TYPE_TECH = 0;

    private ChannelsReadSupport() {}

    static String canonicalLogin(Connection c, String anyCaseLogin) throws SQLException {
        String sql = "SELECT login FROM solana_users WHERE login = ? COLLATE NOCASE LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, anyCaseLogin);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("login") : null;
            }
        }
    }

    static String detectChannelName(Connection c, String ownerBch, int rootNumber) throws SQLException {
        if (rootNumber == 0) return "0";

        String sql = "SELECT block_bytes FROM blocks WHERE bch_name=? AND block_number=? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerBch);
            ps.setInt(2, rootNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                byte[] bytes = rs.getBytes("block_bytes");
                BchBlockEntry e = new BchBlockEntry(bytes);
                BodyRecord body = e.body;
                if (body instanceof CreateChannelBody ccb) return ccb.channelName;
                return null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    static int countPosts(Connection c, String ownerBch, int lineCode) throws SQLException {
        String sql = "SELECT COUNT(*) AS cnt FROM blocks WHERE bch_name=? AND msg_type=? AND msg_sub_type=? AND line_code=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerBch);
            ps.setInt(2, MSG_TYPE_TEXT);
            ps.setInt(3, MsgSubType.TEXT_POST);
            ps.setInt(4, lineCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("cnt") : 0;
            }
        }
    }

    static PostBlock loadLastPost(Connection c, String ownerBch, int lineCode) throws SQLException {
        String sql = """
            SELECT login,bch_name,block_number,block_hash,block_bytes
            FROM blocks
            WHERE bch_name=? AND msg_type=? AND msg_sub_type=? AND line_code=?
            ORDER BY block_number DESC
            LIMIT 1
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerBch);
            ps.setInt(2, MSG_TYPE_TEXT);
            ps.setInt(3, MsgSubType.TEXT_POST);
            ps.setInt(4, lineCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PostBlock pb = new PostBlock();
                pb.login = rs.getString("login");
                pb.bchName = rs.getString("bch_name");
                pb.blockNumber = rs.getInt("block_number");
                pb.blockHash = rs.getBytes("block_hash");
                pb.blockBytes = rs.getBytes("block_bytes");
                return pb;
            }
        }
    }

    static PostBlock loadLastVersion(Connection c, String ownerBch, int originalBlockNumber, byte[] originalHash) throws SQLException {
        String sql = """
            SELECT login,bch_name,block_number,block_hash,block_bytes
            FROM blocks
            WHERE bch_name=? AND msg_type=? AND msg_sub_type=?
              AND to_bch_name=? AND to_block_number=? AND to_block_hash=?
            ORDER BY block_number DESC
            LIMIT 1
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerBch);
            ps.setInt(2, MSG_TYPE_TEXT);
            ps.setInt(3, MsgSubType.TEXT_EDIT_POST);
            ps.setString(4, ownerBch);
            ps.setInt(5, originalBlockNumber);
            ps.setBytes(6, originalHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PostBlock pb = new PostBlock();
                pb.login = rs.getString("login");
                pb.bchName = rs.getString("bch_name");
                pb.blockNumber = rs.getInt("block_number");
                pb.blockHash = rs.getBytes("block_hash");
                pb.blockBytes = rs.getBytes("block_bytes");
                return pb;
            }
        }
    }

    static TextInfo parseTextAndTime(byte[] blockBytes) {
        try {
            BchBlockEntry e = new BchBlockEntry(blockBytes);
            TextInfo ti = new TextInfo();
            ti.createdAtMs = e.timestamp * 1000L;
            if (e.body instanceof TextLineBody tlb) {
                ti.text = tlb.message;
            } else if (e.body instanceof TextReplyBody trb) {
                ti.text = trb.message;
            } else if (e.body instanceof TextBody tb) {
                ti.text = tb.message;
            }
            return ti;
        } catch (Exception ex) {
            return new TextInfo();
        }
    }

    static List<PostBlock> channelPosts(Connection c, String ownerBch, int lineCode, int limit, boolean asc) throws SQLException {
        String order = asc ? "ASC" : "DESC";
        String sql = """
            SELECT login,bch_name,block_number,block_hash,block_bytes
            FROM blocks
            WHERE bch_name=? AND msg_type=? AND msg_sub_type=? AND line_code=?
            ORDER BY block_number
            """ + order + " LIMIT ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerBch);
            ps.setInt(2, MSG_TYPE_TEXT);
            ps.setInt(3, MsgSubType.TEXT_POST);
            ps.setInt(4, lineCode);
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<PostBlock> out = new ArrayList<>();
                while (rs.next()) {
                    PostBlock pb = new PostBlock();
                    pb.login = rs.getString("login");
                    pb.bchName = rs.getString("bch_name");
                    pb.blockNumber = rs.getInt("block_number");
                    pb.blockHash = rs.getBytes("block_hash");
                    pb.blockBytes = rs.getBytes("block_bytes");
                    out.add(pb);
                }
                return out;
            }
        }
    }

    static List<PostBlock> versionsForPost(Connection c, String ownerBch, int originalBlock, byte[] originalHash) throws SQLException {
        String sql = """
            SELECT login,bch_name,block_number,block_hash,block_bytes
            FROM blocks
            WHERE bch_name=? AND msg_type=? AND msg_sub_type=?
              AND to_bch_name=? AND to_block_number=? AND to_block_hash=?
            ORDER BY block_number ASC
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerBch);
            ps.setInt(2, MSG_TYPE_TEXT);
            ps.setInt(3, MsgSubType.TEXT_EDIT_POST);
            ps.setString(4, ownerBch);
            ps.setInt(5, originalBlock);
            ps.setBytes(6, originalHash);
            try (ResultSet rs = ps.executeQuery()) {
                List<PostBlock> out = new ArrayList<>();
                while (rs.next()) {
                    PostBlock pb = new PostBlock();
                    pb.login = rs.getString("login");
                    pb.bchName = rs.getString("bch_name");
                    pb.blockNumber = rs.getInt("block_number");
                    pb.blockHash = rs.getBytes("block_hash");
                    pb.blockBytes = rs.getBytes("block_bytes");
                    out.add(pb);
                }
                return out;
            }
        }
    }

    static int[] loadStats(Connection c, String bch, int blockNumber, byte[] blockHash) throws SQLException {
        String sql = "SELECT likes_count,replies_count FROM message_stats WHERE to_bch_name=? AND to_block_number=? AND to_block_hash=? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bch);
            ps.setInt(2, blockNumber);
            ps.setBytes(3, blockHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new int[] {0, 0};
                return new int[] {rs.getInt("likes_count"), rs.getInt("replies_count")};
            }
        }
    }

    static String detectChannelDescription(Connection c, String ownerBch, int rootNumber) throws SQLException {
        if (rootNumber == 0) return "";

        // Preferred source: persisted state (fast path, works for CreateChannelBody v2).
        String stateSql = """
            SELECT channel_description
            FROM channel_names_state
            WHERE owner_bch_name = ? AND channel_root_block_number = ?
            LIMIT 1
            """;
        try (PreparedStatement ps = c.prepareStatement(stateSql)) {
            ps.setString(1, ownerBch);
            ps.setInt(2, rootNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return String.valueOf(rs.getString("channel_description") == null ? "" : rs.getString("channel_description"));
                }
            }
        } catch (SQLException ignored) {
            // keep compatibility for environments where table schema is older/corrupted
        }

        // Fallback: parse root block directly.
        String sql = "SELECT block_bytes FROM blocks WHERE bch_name=? AND block_number=? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerBch);
            ps.setInt(2, rootNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "";
                byte[] bytes = rs.getBytes("block_bytes");
                BchBlockEntry e = new BchBlockEntry(bytes);
                BodyRecord body = e.body;
                if (body instanceof CreateChannelBody ccb) return ccb.channelDescription == null ? "" : ccb.channelDescription;
                return "";
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    static boolean isLikedByLogin(Connection c, String login, String toBch, int toBlockNumber, byte[] toBlockHash) throws SQLException {
        if (login == null || login.isBlank() || toBch == null || toBch.isBlank() || toBlockHash == null || toBlockHash.length != 32) {
            return false;
        }
        String sql = """
            SELECT msg_sub_type
            FROM blocks
            WHERE login = ? COLLATE NOCASE
              AND msg_type = ?
              AND to_bch_name = ?
              AND to_block_number = ?
              AND to_block_hash = ?
            ORDER BY block_number DESC
            LIMIT 1
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setInt(2, MSG_TYPE_REACTION);
            ps.setString(3, toBch);
            ps.setInt(4, toBlockNumber);
            ps.setBytes(5, toBlockHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                return rs.getInt("msg_sub_type") == MsgSubType.REACTION_LIKE;
            }
        }
    }

    static byte[] hexToBytes(String s) {
        if (s == null) return null;
        String x = s.trim();
        if ((x.length() & 1) != 0) throw new IllegalArgumentException("hex length must be even");
        byte[] out = new byte[x.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(x.charAt(i * 2), 16);
            int lo = Character.digit(x.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    static String toHex(byte[] bytes) {
        if (bytes == null) return null;
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static final class PostBlock {
        String login;
        String bchName;
        int blockNumber;
        byte[] blockHash;
        byte[] blockBytes;
    }

    static final class TextInfo {
        String text = "";
        long createdAtMs = 0L;
    }
}
