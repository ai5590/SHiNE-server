package server.logic.ws_protocol.JSON.handlers.connections.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.ArrayList;
import java.util.List;

public class Net_ListContacts_Response extends Net_Response {
    private String login;
    private List<String> contacts = new ArrayList<>();

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public List<String> getContacts() { return contacts; }
    public void setContacts(List<String> contacts) { this.contacts = contacts; }
}
