package server.logic.ws_protocol.JSON.handlers.channels.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.ArrayList;
import java.util.List;

public class Net_ListSubscriptionsFeed_Response extends Net_Response {
    private String login;
    private List<ChannelSummary> ownedChannels = new ArrayList<>();
    private List<ChannelSummary> followedUsersChannels = new ArrayList<>();
    private List<ChannelSummary> followedChannels = new ArrayList<>();

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public List<ChannelSummary> getOwnedChannels() { return ownedChannels; }
    public void setOwnedChannels(List<ChannelSummary> ownedChannels) { this.ownedChannels = ownedChannels; }

    public List<ChannelSummary> getFollowedUsersChannels() { return followedUsersChannels; }
    public void setFollowedUsersChannels(List<ChannelSummary> followedUsersChannels) { this.followedUsersChannels = followedUsersChannels; }

    public List<ChannelSummary> getFollowedChannels() { return followedChannels; }
    public void setFollowedChannels(List<ChannelSummary> followedChannels) { this.followedChannels = followedChannels; }

    public static class ChannelSummary {
        private ChannelRef channel;
        private int messagesCount;
        private LastMessage lastMessage;

        public ChannelRef getChannel() { return channel; }
        public void setChannel(ChannelRef channel) { this.channel = channel; }

        public int getMessagesCount() { return messagesCount; }
        public void setMessagesCount(int messagesCount) { this.messagesCount = messagesCount; }

        public LastMessage getLastMessage() { return lastMessage; }
        public void setLastMessage(LastMessage lastMessage) { this.lastMessage = lastMessage; }
    }

    public static class ChannelRef {
        private String ownerLogin;
        private String ownerBlockchainName;
        private String channelName;
        private String channelDescription;
        private boolean personal;
        private BlockRef channelRoot;

        public String getOwnerLogin() { return ownerLogin; }
        public void setOwnerLogin(String ownerLogin) { this.ownerLogin = ownerLogin; }

        public String getOwnerBlockchainName() { return ownerBlockchainName; }
        public void setOwnerBlockchainName(String ownerBlockchainName) { this.ownerBlockchainName = ownerBlockchainName; }

        public String getChannelName() { return channelName; }
        public void setChannelName(String channelName) { this.channelName = channelName; }

        public String getChannelDescription() { return channelDescription; }
        public void setChannelDescription(String channelDescription) { this.channelDescription = channelDescription; }

        public boolean isPersonal() { return personal; }
        public void setPersonal(boolean personal) { this.personal = personal; }

        public BlockRef getChannelRoot() { return channelRoot; }
        public void setChannelRoot(BlockRef channelRoot) { this.channelRoot = channelRoot; }
    }

    public static class LastMessage {
        private BlockRef messageRef;
        private String text;
        private long createdAtMs;
        private String authorLogin;
        private String authorBlockchainName;

        public BlockRef getMessageRef() { return messageRef; }
        public void setMessageRef(BlockRef messageRef) { this.messageRef = messageRef; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public long getCreatedAtMs() { return createdAtMs; }
        public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

        public String getAuthorLogin() { return authorLogin; }
        public void setAuthorLogin(String authorLogin) { this.authorLogin = authorLogin; }

        public String getAuthorBlockchainName() { return authorBlockchainName; }
        public void setAuthorBlockchainName(String authorBlockchainName) { this.authorBlockchainName = authorBlockchainName; }
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
