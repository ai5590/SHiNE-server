package server.logic.ws_protocol.JSON.handlers.connections.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Ответ GetFriendsLists.
 *
 * {
 *   "op": "GetFriendsLists",
 *   "requestId": "req-100",
 *   "status": 200,
 *   "payload": {
 *     "login": "Anya",                  // канонический регистр из БД
 *     "out_friends": ["Bob", "Kate"],   // кому login поставил FRIEND
 *     "in_friends":  ["Alex", "Kate"]   // кто поставил FRIEND login
 *   }
 * }
 */
public class Net_GetFriendsLists_Response extends Net_Response {

    private String login;

    private List<String> out_friends = new ArrayList<>();
    private List<String> in_friends  = new ArrayList<>();

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public List<String> getOut_friends() { return out_friends; }
    public void setOut_friends(List<String> out_friends) { this.out_friends = out_friends; }

    public List<String> getIn_friends() { return in_friends; }
    public void setIn_friends(List<String> in_friends) { this.in_friends = in_friends; }
}