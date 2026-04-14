package server.logic.ws_protocol.JSON.handlers.channels.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.ArrayList;
import java.util.List;

public class Net_GetChannelMessages_Response extends Net_Response {
    private Channel channel;
    private List<MessageItem> messages = new ArrayList<>();

    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }

    public List<MessageItem> getMessages() { return messages; }
    public void setMessages(List<MessageItem> messages) { this.messages = messages; }

    public static class Channel {
        private String ownerLogin;
        private String ownerBlockchainName;
        private String channelName;
        private String channelDescription;
        private BlockRef channelRoot;

        public String getOwnerLogin() { return ownerLogin; }
        public void setOwnerLogin(String ownerLogin) { this.ownerLogin = ownerLogin; }

        public String getOwnerBlockchainName() { return ownerBlockchainName; }
        public void setOwnerBlockchainName(String ownerBlockchainName) { this.ownerBlockchainName = ownerBlockchainName; }

        public String getChannelName() { return channelName; }
        public void setChannelName(String channelName) { this.channelName = channelName; }

        public String getChannelDescription() { return channelDescription; }
        public void setChannelDescription(String channelDescription) { this.channelDescription = channelDescription; }

        public BlockRef getChannelRoot() { return channelRoot; }
        public void setChannelRoot(BlockRef channelRoot) { this.channelRoot = channelRoot; }
    }

    public static class MessageItem {
        private BlockRef messageRef;
        private String authorLogin;
        private String authorBlockchainName;
        private long createdAtMs;
        private String text;
        private int likesCount;
        private boolean likedByMe;
        private int repliesCount;
        private int versionsTotal;
        private List<VersionItem> versions = new ArrayList<>();

        public BlockRef getMessageRef() { return messageRef; }
        public void setMessageRef(BlockRef messageRef) { this.messageRef = messageRef; }

        public String getAuthorLogin() { return authorLogin; }
        public void setAuthorLogin(String authorLogin) { this.authorLogin = authorLogin; }

        public String getAuthorBlockchainName() { return authorBlockchainName; }
        public void setAuthorBlockchainName(String authorBlockchainName) { this.authorBlockchainName = authorBlockchainName; }

        public long getCreatedAtMs() { return createdAtMs; }
        public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public int getLikesCount() { return likesCount; }
        public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

        public boolean isLikedByMe() { return likedByMe; }
        public void setLikedByMe(boolean likedByMe) { this.likedByMe = likedByMe; }

        public int getRepliesCount() { return repliesCount; }
        public void setRepliesCount(int repliesCount) { this.repliesCount = repliesCount; }

        public int getVersionsTotal() { return versionsTotal; }
        public void setVersionsTotal(int versionsTotal) { this.versionsTotal = versionsTotal; }

        public List<VersionItem> getVersions() { return versions; }
        public void setVersions(List<VersionItem> versions) { this.versions = versions; }
    }

    public static class VersionItem {
        private int versionIndex;
        private int blockNumber;
        private String blockHash;
        private String text;
        private long createdAtMs;

        public int getVersionIndex() { return versionIndex; }
        public void setVersionIndex(int versionIndex) { this.versionIndex = versionIndex; }

        public int getBlockNumber() { return blockNumber; }
        public void setBlockNumber(int blockNumber) { this.blockNumber = blockNumber; }

        public String getBlockHash() { return blockHash; }
        public void setBlockHash(String blockHash) { this.blockHash = blockHash; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public long getCreatedAtMs() { return createdAtMs; }
        public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
    }

    public static class BlockRef {
        private int blockNumber;
        private String blockHash;

        public int getBlockNumber() { return blockNumber; }
        public void setBlockNumber(int blockNumber) { this.blockNumber = blockNumber; }

        public String getBlockHash() { return blockHash; }
        public void setBlockHash(String blockHash) { this.blockHash = blockHash; }
    }
}
