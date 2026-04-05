package server.logic.ws_protocol.JSON.messages;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.messages.entyties.Net_UpsertPushToken_Request;
import server.logic.ws_protocol.JSON.messages.entyties.Net_UpsertPushToken_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.PushTokensDAO;
import shine.db.entities.PushTokenEntry;

public class Net_UpsertPushToken_Handler implements JsonMessageHandler {
    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_UpsertPushToken_Request req = (Net_UpsertPushToken_Request) baseRequest;
        if (ctx == null || !ctx.isAuthenticatedUser()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NOT_AUTHENTICATED", "Требуется авторизация");
        }
        if (req.getTokenId() == null || req.getTokenId().isBlank() || req.getToken() == null || req.getToken().isBlank()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_FIELDS", "tokenId и token обязательны");
        }

        PushTokenEntry e = new PushTokenEntry();
        e.setTokenId(req.getTokenId().trim());
        e.setLogin(ctx.getLogin());
        e.setSessionId((req.getSessionId() == null || req.getSessionId().isBlank()) ? ctx.getSessionId() : req.getSessionId().trim());
        e.setProvider(req.getProvider() == null || req.getProvider().isBlank() ? "fcm" : req.getProvider().trim());
        e.setToken(req.getToken().trim());
        e.setPlatform(req.getPlatform());
        e.setUserAgent(req.getUserAgent());
        e.setUpdatedAtMs(System.currentTimeMillis());
        PushTokensDAO.getInstance().upsert(e);

        Net_UpsertPushToken_Response resp = new Net_UpsertPushToken_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setTokenId(e.getTokenId());
        resp.setUpdatedAtMs(e.getUpdatedAtMs());
        return resp;
    }
}
