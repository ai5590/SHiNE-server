package server.logic.ws_protocol.JSON.handlers.system.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Pong-ответ:
 * {
 *   "op": "Ping",
 *   "requestId": "req-1",
 *   "status": 200,
 *   "payload": { "ts": 1700000000123 }
 * }
 */
public class Net_Ping_Response extends Net_Response {

    private long ts;

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }
}