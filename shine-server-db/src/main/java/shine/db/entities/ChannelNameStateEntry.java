package shine.db.entities;

import java.util.Arrays;

public class ChannelNameStateEntry {
    private String slug;
    private String displayName;
    private String channelDescription;
    private String ownerLogin;
    private String ownerBlockchainName;
    private int channelRootBlockNumber;
    private byte[] channelRootBlockHash;
    private long createdAtMs;

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getChannelDescription() {
        return channelDescription;
    }

    public void setChannelDescription(String channelDescription) {
        this.channelDescription = channelDescription;
    }

    public String getOwnerLogin() {
        return ownerLogin;
    }

    public void setOwnerLogin(String ownerLogin) {
        this.ownerLogin = ownerLogin;
    }

    public String getOwnerBlockchainName() {
        return ownerBlockchainName;
    }

    public void setOwnerBlockchainName(String ownerBlockchainName) {
        this.ownerBlockchainName = ownerBlockchainName;
    }

    public int getChannelRootBlockNumber() {
        return channelRootBlockNumber;
    }

    public void setChannelRootBlockNumber(int channelRootBlockNumber) {
        this.channelRootBlockNumber = channelRootBlockNumber;
    }

    public byte[] getChannelRootBlockHash() {
        return channelRootBlockHash == null ? null : Arrays.copyOf(channelRootBlockHash, channelRootBlockHash.length);
    }

    public void setChannelRootBlockHash(byte[] channelRootBlockHash) {
        this.channelRootBlockHash = channelRootBlockHash == null ? null : Arrays.copyOf(channelRootBlockHash, channelRootBlockHash.length);
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(long createdAtMs) {
        this.createdAtMs = createdAtMs;
    }
}
