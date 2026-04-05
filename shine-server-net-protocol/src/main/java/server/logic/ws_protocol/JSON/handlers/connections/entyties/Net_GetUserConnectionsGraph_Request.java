package server.logic.ws_protocol.JSON.handlers.connections.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_GetUserConnectionsGraph_Request extends Net_Request {
    private String login;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
}
