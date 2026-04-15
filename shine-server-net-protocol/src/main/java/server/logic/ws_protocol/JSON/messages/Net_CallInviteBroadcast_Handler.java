package server.logic.ws_protocol.JSON.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.messages.entyties.Net_CallInviteBroadcast_Request;
import server.logic.ws_protocol.JSON.messages.entyties.Net_CallInviteBroadcast_Response;
import server.logic.ws_protocol.JSON.push.FcmPushSender;
import server.logic.ws_protocol.JSON.push.WsEventSender;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.JSON.utils.NetIdGenerator;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.PushTokensDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.PushTokenEntry;
import shine.db.entities.SolanaUserEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Net_CallInviteBroadcast_Handler implements JsonMessageHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TYPE_INVITE = 100;

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_CallInviteBroadcast_Request req = (Net_CallInviteBroadcast_Request) baseRequest;
        if (ctx == null || !ctx.isAuthenticatedUser()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NOT_AUTHENTICATED", "Требуется авторизация");
        }

        String toRequest = req.getToLogin() == null ? "" : req.getToLogin().trim();
        String callId = req.getCallId() == null ? "" : req.getCallId().trim();
        int type = req.getType() == null ? TYPE_INVITE : req.getType();
        if (toRequest.isBlank() || callId.isBlank() || type != TYPE_INVITE) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_FIELDS", "toLogin/callId/type=100 обязательны");
        }

        SolanaUserEntry targetUser = SolanaUsersDAO.getInstance().getByLogin(toRequest);
        if (targetUser == null) {
            return NetExceptionResponseFactory.error(req, 404, "USER_NOT_FOUND", "Пользователь не найден");
        }

        String from = ctx.getLogin();
        String to = targetUser.getLogin();
        long timeMs = System.currentTimeMillis();

        Set<ConnectionContext> activeSessions = ActiveConnectionsRegistry.getInstance().getByLogin(to);
        List<PushTokenEntry> tokens = PushTokensDAO.getInstance().listByLogin(to);

        int wsDelivered = 0;
        int fcmDelivered = 0;
        Set<String> activeSessionIds = new HashSet<>();

        for (ConnectionContext targetCtx : activeSessions) {
            activeSessionIds.add(targetCtx.getSessionId());

            String eventId = NetIdGenerator.eventId("evt");
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("eventId", eventId);
            payload.put("fromLogin", from);
            payload.put("fromSessionId", ctx.getSessionId());
            payload.put("toLogin", to);
            payload.put("callId", callId);
            payload.put("type", TYPE_INVITE);
            payload.put("timeMs", timeMs);

            boolean sent = WsEventSender.sendEvent(targetCtx, "IncomingCallInvite", eventId, payload);
            if (sent) wsDelivered++;
        }

        for (PushTokenEntry token : tokens) {
            boolean pushed = FcmPushSender.sendNotification(
                    token.getToken(),
                    "Входящий звонок",
                    from + " пытается дозвониться",
                    callId
            );
            if (pushed) fcmDelivered++;
        }

        Net_CallInviteBroadcast_Response resp = new Net_CallInviteBroadcast_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setCallId(callId);
        resp.setDeliveredWsSessions(wsDelivered);
        resp.setDeliveredFcmSessions(fcmDelivered);
        return resp;
    }
}
