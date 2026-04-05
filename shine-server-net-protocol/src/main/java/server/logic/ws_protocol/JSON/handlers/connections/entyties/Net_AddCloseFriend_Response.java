package server.logic.ws_protocol.JSON.handlers.connections.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

public class Net_AddCloseFriend_Response extends Net_Response {
    private String login;
    private String toLogin;
    private String relation;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
}
