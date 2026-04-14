package server.logic.ws_protocol.JSON.handlers.connections.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.ArrayList;
import java.util.List;

public class Net_GetUserConnectionsGraph_Response extends Net_Response {
    private String login;
    private List<String> outFriends = new ArrayList<>();
    private List<String> inFriends = new ArrayList<>();
    private List<String> outContacts = new ArrayList<>();
    private List<String> inContacts = new ArrayList<>();
    private List<String> outFollows = new ArrayList<>();
    private List<String> inFollows = new ArrayList<>();

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public List<String> getOutFriends() { return outFriends; }
    public void setOutFriends(List<String> outFriends) { this.outFriends = outFriends; }
    public List<String> getInFriends() { return inFriends; }
    public void setInFriends(List<String> inFriends) { this.inFriends = inFriends; }
    public List<String> getOutContacts() { return outContacts; }
    public void setOutContacts(List<String> outContacts) { this.outContacts = outContacts; }
    public List<String> getInContacts() { return inContacts; }
    public void setInContacts(List<String> inContacts) { this.inContacts = inContacts; }
    public List<String> getOutFollows() { return outFollows; }
    public void setOutFollows(List<String> outFollows) { this.outFollows = outFollows; }
    public List<String> getInFollows() { return inFollows; }
    public void setInFollows(List<String> inFollows) { this.inFollows = inFollows; }
}
