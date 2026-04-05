package server.logic.ws_protocol.JSON.handlers.connections.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.ArrayList;
import java.util.List;

public class Net_GetUserConnectionsGraph_Response extends Net_Response {
    private String login;
    private List<String> outFriends = new ArrayList<>();
    private List<String> inFriends = new ArrayList<>();

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public List<String> getOutFriends() { return outFriends; }
    public void setOutFriends(List<String> outFriends) { this.outFriends = outFriends; }
    public List<String> getInFriends() { return inFriends; }
    public void setInFriends(List<String> inFriends) { this.inFriends = inFriends; }
}
