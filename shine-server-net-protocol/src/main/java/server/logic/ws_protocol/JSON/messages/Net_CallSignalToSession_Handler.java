package server.logic.ws_protocol.JSON.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.messages.entyties.Net_CallSignalToSession_Request;
import server.logic.ws_protocol.JSON.messages.entyties.Net_CallSignalToSession_Response;
import server.logic.ws_protocol.JSON.push.WsEventSender;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.JSON.utils.NetIdGenerator;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.SolanaUserEntry;

public class Net_CallSignalToSession_Handler implements JsonMessageHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_CallSignalToSession_Request req = (Net_CallSignalToSession_Request) baseRequest;
        if (ctx == null || !ctx.isAuthenticatedUser()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NOT_AUTHENTICATED", "Требуется авторизация");
        }

        String toRequest = req.getToLogin() == null ? "" : req.getToLogin().trim();
        String targetSessionId = req.getTargetSessionId() == null ? "" : req.getTargetSessionId().trim();
        String callId = req.getCallId() == null ? "" : req.getCallId().trim();
        Integer type = req.getType();
        String data = req.getData() == null ? "" : req.getData();

        if (toRequest.isBlank() || targetSessionId.isBlank() || callId.isBlank() || type == null) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_FIELDS", "toLogin/targetSessionId/callId/type обязательны");
        }

        SolanaUserEntry targetUser = SolanaUsersDAO.getInstance().getByLogin(toRequest);
        if (targetUser == null) {
            return NetExceptionResponseFactory.error(req, 404, "USER_NOT_FOUND", "Пользователь не найден");
        }
        String to = targetUser.getLogin();

        ConnectionContext targetCtx = ActiveConnectionsRegistry.getInstance().getBySessionId(targetSessionId);
        if (targetCtx == null || !to.equalsIgnoreCase(targetCtx.getLogin())) {
            return NetExceptionResponseFactory.error(req, 404, "SESSION_NOT_FOUND", "Целевая сессия не найдена");
        }

        String eventId = NetIdGenerator.eventId("evt");
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("eventId", eventId);
        payload.put("fromLogin", ctx.getLogin());
        payload.put("fromSessionId", ctx.getSessionId());
        payload.put("toLogin", to);
        payload.put("callId", callId);
        payload.put("type", type);
        payload.put("data", data);
        payload.put("timeMs", System.currentTimeMillis());

        boolean delivered = WsEventSender.sendEvent(targetCtx, "IncomingCallSignal", eventId, payload);

        Net_CallSignalToSession_Response resp = new Net_CallSignalToSession_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(delivered ? WireCodes.Status.OK : 404);
        resp.setDelivered(delivered);
        return resp;
    }
}
