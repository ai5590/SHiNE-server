package server.logic.ws_protocol.JSON.handlers.system;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.system.entyties.Net_Ping_Request;
import server.logic.ws_protocol.JSON.handlers.system.entyties.Net_Ping_Response;
import server.logic.ws_protocol.WireCodes;

/**
 * Ping — keep-alive.
 * В ответ кладём только ts (текущее время сервера в мс).
 */
public class Net_Ping_Handler implements JsonMessageHandler {

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_Ping_Request req = (Net_Ping_Request) baseRequest;

        Net_Ping_Response resp = new Net_Ping_Response();
        resp.setOp(req.getOp());            // "Ping"
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);

        // ничего не проверяем, просто отдаём серверное время
        resp.setTs(System.currentTimeMillis());

        return resp;
    }
}