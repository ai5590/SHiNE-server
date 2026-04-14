package server.logic.ws_protocol.JSON.messages;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.messages.entyties.Net_UpsertPushToken_Request;
import server.logic.ws_protocol.JSON.messages.entyties.Net_UpsertPushToken_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;

public class Net_UpsertPushToken_Handler implements JsonMessageHandler {
    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_UpsertPushToken_Request req = (Net_UpsertPushToken_Request) baseRequest;
        if (ctx == null || !ctx.isAuthenticatedUser()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NOT_AUTHENTICATED", "Требуется авторизация");
        }
        if (req.getEndpoint() == null || req.getEndpoint().isBlank()
                || req.getP256dhKey() == null || req.getP256dhKey().isBlank()
                || req.getAuthKey() == null || req.getAuthKey().isBlank()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_FIELDS", "endpoint/p256dhKey/authKey обязательны");
        }

        String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank()) ? ctx.getSessionId() : req.getSessionId().trim();
        long now = System.currentTimeMillis();
        ActiveSessionsDAO.getInstance().updatePushSubscription(
                sessionId,
                req.getEndpoint().trim(),
                req.getP256dhKey().trim(),
                req.getAuthKey().trim()
        );

        Net_UpsertPushToken_Response resp = new Net_UpsertPushToken_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setTokenId(sessionId);
        resp.setUpdatedAtMs(now);
        return resp;
    }
}
