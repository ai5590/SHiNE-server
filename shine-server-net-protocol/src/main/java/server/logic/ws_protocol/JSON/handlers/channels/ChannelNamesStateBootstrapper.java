package server.logic.ws_protocol.JSON.handlers.channels;

import blockchain.BchBlockEntry;
import blockchain.body.CreateChannelBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shine.db.SqliteDbController;
import shine.db.channels.ChannelNameRules;
import shine.db.dao.ChannelNameStateDAO;
import shine.db.entities.ChannelNameStateEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChannelNamesStateBootstrapper {
    private static final Logger log = LoggerFactory.getLogger(ChannelNamesStateBootstrapper.class);
    private static final int MSG_TYPE_TECH = 0;
    private static final int MSG_SUB_TYPE_CREATE_CHANNEL = 1;
    private static volatile boolean bootstrapped;

    private ChannelNamesStateBootstrapper() {}

    public static void bootstrapOrFailFast() {
        if (bootstrapped) return;
        synchronized (ChannelNamesStateBootstrapper.class) {
            if (bootstrapped) return;
            rebuildFromBlocksOrThrow();
            bootstrapped = true;
        }
    }

    private static void rebuildFromBlocksOrThrow() {
        ChannelNameStateDAO dao = ChannelNameStateDAO.getInstance();
        List<ChannelNameStateEntry> entries = new ArrayList<>();
        Map<String, String> slugToIdentity = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        String sql = """
                SELECT login, bch_name, block_number, block_hash, block_bytes
                FROM blocks
                WHERE msg_type = ? AND msg_sub_type = ?
                ORDER BY bch_name, block_number
                """;

        try (Connection c = SqliteDbController.getInstance().getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, MSG_TYPE_TECH);
                    ps.setInt(2, MSG_SUB_TYPE_CREATE_CHANNEL);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String ownerLogin = rs.getString("login");
                            String ownerBch = rs.getString("bch_name");
                            int blockNumber = rs.getInt("block_number");
                            byte[] blockHash = rs.getBytes("block_hash");
                            byte[] blockBytes = rs.getBytes("block_bytes");

                            final BchBlockEntry parsed;
                            final CreateChannelBody createChannelBody;
                            try {
                                parsed = new BchBlockEntry(blockBytes);
                                if (!(parsed.body instanceof CreateChannelBody ccb)) continue;
                                createChannelBody = ccb;
                            } catch (Exception parseError) {
                                skipped.add(ownerBch + "#" + blockNumber + " (parse_error)");
                                continue;
                            }

                            final String displayName;
                            final String slug;
                            try {
                                displayName = ChannelNameRules.normalizeDisplayName(createChannelBody.channelName);
                                slug = ChannelNameRules.toCanonicalSlug(displayName);
                            } catch (Exception badName) {
                                skipped.add(ownerBch + "#" + blockNumber + " (invalid_name)");
                                continue;
                            }

                            String identity = ownerBch + "#" + blockNumber;
                            String existing = slugToIdentity.putIfAbsent(slug, identity);
                            if (existing != null && !existing.equals(identity)) {
                                conflicts.add("slug=\"" + slug + "\" conflicts: " + existing + " vs " + identity);
                                continue;
                            }

                            ChannelNameStateEntry entry = new ChannelNameStateEntry();
                            entry.setSlug(slug);
                            entry.setDisplayName(displayName);
                            entry.setOwnerLogin(ownerLogin);
                            entry.setOwnerBlockchainName(ownerBch);
                            entry.setChannelRootBlockNumber(blockNumber);
                            entry.setChannelRootBlockHash(blockHash);
                            entry.setCreatedAtMs(parsed.timestamp * 1000L);
                            entries.add(entry);
                        }
                    }
                }

                dao.clearAll(c);
                dao.insertAll(c, entries);
                c.commit();
                log.info("channel_names_state bootstrapped: {}", entries.size());
                if (!conflicts.isEmpty()) {
                    log.warn("channel_names_state bootstrap detected {} slug conflicts (kept first occurrence)", conflicts.size());
                    int preview = Math.min(conflicts.size(), 10);
                    for (int i = 0; i < preview; i++) {
                        log.warn("channel_names_state conflict: {}", conflicts.get(i));
                    }
                }
                if (!skipped.isEmpty()) {
                    log.warn("channel_names_state bootstrap skipped {} legacy entries", skipped.size());
                    int preview = Math.min(skipped.size(), 10);
                    for (int i = 0; i < preview; i++) {
                        log.warn("channel_names_state skipped: {}", skipped.get(i));
                    }
                }
            } catch (Exception e) {
                try {
                    c.rollback();
                } catch (Exception ignored) {
                }
                throw e;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to bootstrap channel_names_state", e);
        }
    }
}
