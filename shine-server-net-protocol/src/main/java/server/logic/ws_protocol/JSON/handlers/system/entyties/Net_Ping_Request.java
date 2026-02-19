package server.logic.ws_protocol.JSON.handlers.system.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Ping:
 * {
 *   "op": "Ping",
 *   "requestId": "req-1",
 *   "payload": { "ts": 1700000000000 }
 * }
 *
 * Сервер ничего не проверяет, поле ts можно слать любое.
 */
public class Net_Ping_Request extends Net_Request {

    private long ts;

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }
}