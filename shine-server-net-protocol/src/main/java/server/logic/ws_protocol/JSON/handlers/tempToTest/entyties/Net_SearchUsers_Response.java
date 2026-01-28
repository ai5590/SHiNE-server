package server.logic.ws_protocol.JSON.handlers.tempToTest.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Ответ SearchUsers.
 *
 * Всегда status=200.
 *
 * Пример:
 * {
 *   "op": "SearchUsers",
 *   "requestId": "su-1",
 *   "status": 200,
 *   "payload": {
 *     "logins": ["Anya", "andrew", "Angel"]
 *   }
 * }
 */
public class Net_SearchUsers_Response extends Net_Response {

    private List<String> logins = new ArrayList<>();

    public List<String> getLogins() { return logins; }
    public void setLogins(List<String> logins) { this.logins = logins; }
}