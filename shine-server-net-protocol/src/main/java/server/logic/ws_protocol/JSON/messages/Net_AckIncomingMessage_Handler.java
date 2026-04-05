package server.logic.ws_protocol.JSON.messages;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.messages.entyties.Net_AckIncomingMessage_Request;
import server.logic.ws_protocol.JSON.messages.entyties.Net_AckIncomingMessage_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;

public class Net_AckIncomingMessage_Handler implements JsonMessageHandler {
    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_AckIncomingMessage_Request req = (Net_AckIncomingMessage_Request) baseRequest;
        if (ctx == null || !ctx.isAuthenticatedUser()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NOT_AUTHENTICATED", "Требуется авторизация");
        }
        if (req.getEventId() != null && !req.getEventId().isBlank()) {
            DeliveryTracker.getInstance().ack(req.getEventId());
        }

        Net_AckIncomingMessage_Response resp = new Net_AckIncomingMessage_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        return resp;
    }
}
