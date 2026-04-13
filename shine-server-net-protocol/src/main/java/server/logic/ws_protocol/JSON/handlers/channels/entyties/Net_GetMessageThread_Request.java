package server.logic.ws_protocol.JSON.handlers.channels.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_GetMessageThread_Request extends Net_Request {
    private String login;
    private MessageSelector message;
    private Integer depthUp;
    private Integer depthDown;
    private Integer limitChildrenPerNode;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public MessageSelector getMessage() { return message; }
    public void setMessage(MessageSelector message) { this.message = message; }

    public Integer getDepthUp() { return depthUp; }
    public void setDepthUp(Integer depthUp) { this.depthUp = depthUp; }

    public Integer getDepthDown() { return depthDown; }
    public void setDepthDown(Integer depthDown) { this.depthDown = depthDown; }

    public Integer getLimitChildrenPerNode() { return limitChildrenPerNode; }
    public void setLimitChildrenPerNode(Integer limitChildrenPerNode) { this.limitChildrenPerNode = limitChildrenPerNode; }

    public static class MessageSelector {
        private String blockchainName;
        private Integer blockNumber;
        private String blockHash;

        public String getBlockchainName() { return blockchainName; }
        public void setBlockchainName(String blockchainName) { this.blockchainName = blockchainName; }

        public Integer getBlockNumber() { return blockNumber; }
        public void setBlockNumber(Integer blockNumber) { this.blockNumber = blockNumber; }

        public String getBlockHash() { return blockHash; }
        public void setBlockHash(String blockHash) { this.blockHash = blockHash; }
    }
}
