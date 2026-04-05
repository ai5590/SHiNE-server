package server.logic.ws_protocol.JSON.messages.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_UpsertPushToken_Request extends Net_Request {
    private String tokenId;
    private String sessionId;
    private String provider;
    private String token;
    private String platform;
    private String userAgent;

    public String getTokenId() { return tokenId; }
    public void setTokenId(String tokenId) { this.tokenId = tokenId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
