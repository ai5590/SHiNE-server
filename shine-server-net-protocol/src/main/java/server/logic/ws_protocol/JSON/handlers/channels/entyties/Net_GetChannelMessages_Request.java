package server.logic.ws_protocol.JSON.handlers.channels.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_GetChannelMessages_Request extends Net_Request {
    private String login;
    private ChannelSelector channel;
    private Integer limit;
    private String sort;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public ChannelSelector getChannel() { return channel; }
    public void setChannel(ChannelSelector channel) { this.channel = channel; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }

    public static class ChannelSelector {
        private String ownerBlockchainName;
        private Integer channelRootBlockNumber;
        private String channelRootBlockHash;

        public String getOwnerBlockchainName() { return ownerBlockchainName; }
        public void setOwnerBlockchainName(String ownerBlockchainName) { this.ownerBlockchainName = ownerBlockchainName; }

        public Integer getChannelRootBlockNumber() { return channelRootBlockNumber; }
        public void setChannelRootBlockNumber(Integer channelRootBlockNumber) { this.channelRootBlockNumber = channelRootBlockNumber; }

        public String getChannelRootBlockHash() { return channelRootBlockHash; }
        public void setChannelRootBlockHash(String channelRootBlockHash) { this.channelRootBlockHash = channelRootBlockHash; }
    }
}
