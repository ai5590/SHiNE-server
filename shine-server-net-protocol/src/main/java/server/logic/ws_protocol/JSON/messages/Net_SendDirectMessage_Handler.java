package server.logic.ws_protocol.JSON.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.messages.entyties.Net_SendDirectMessage_Request;
import server.logic.ws_protocol.JSON.messages.entyties.Net_SendDirectMessage_Response;
import server.logic.ws_protocol.JSON.push.FcmPushSender;
import server.logic.ws_protocol.JSON.push.WsEventSender;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.JSON.utils.NetIdGenerator;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.DirectMessagesDAO;
import shine.db.dao.PushTokensDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.DirectMessageEntry;
import shine.db.entities.PushTokenEntry;
import shine.db.entities.SolanaUserEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Net_SendDirectMessage_Handler implements JsonMessageHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_SendDirectMessage_Request req = (Net_SendDirectMessage_Request) baseRequest;
        if (ctx == null || !ctx.isAuthenticatedUser()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NOT_AUTHENTICATED", "Требуется авторизация");
        }
        if (req.getToLogin() == null || req.getToLogin().isBlank() || req.getText() == null || req.getText().isBlank()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_FIELDS", "toLogin и text обязательны");
        }

        String from = ctx.getLogin();
        String toRequest = req.getToLogin().trim();
        String text = req.getText().trim();

        SolanaUserEntry targetUser = SolanaUsersDAO.getInstance().getByLogin(toRequest);
        if (targetUser == null) {
            return NetExceptionResponseFactory.error(req, 404, "USER_NOT_FOUND", "Пользователь не найден");
        }
        String to = targetUser.getLogin();

        if (!canSend(from, to)) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NO_PERMISSION", "Можно писать только контактам или тем, кто уже писал вам");
        }

        String messageId = NetIdGenerator.eventId("msg");
        DirectMessageEntry entry = new DirectMessageEntry();
        entry.setMessageId(messageId);
        entry.setFromLogin(from);
        entry.setToLogin(to);
        entry.setText(text);
        entry.setCreatedAtMs(System.currentTimeMillis());
        DirectMessagesDAO.getInstance().insert(entry);

        Set<ConnectionContext> activeSessions = ActiveConnectionsRegistry.getInstance().getByLogin(to);
        List<PushTokenEntry> tokens = PushTokensDAO.getInstance().listByLogin(to);

        int wsDelivered = 0;
        int fcmDelivered = 0;

        Set<String> activeSessionIds = new HashSet<>();
        for (ConnectionContext targetCtx : activeSessions) {
            activeSessionIds.add(targetCtx.getSessionId());
            String eventId = NetIdGenerator.eventId("evt");
            CompletableFuture<Boolean> waiter = DeliveryTracker.getInstance().register(eventId);

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("eventId", eventId);
            payload.put("messageId", messageId);
            payload.put("fromLogin", from);
            payload.put("toLogin", to);
            payload.put("text", text);
            payload.put("timeMs", entry.getCreatedAtMs());

            boolean sent = WsEventSender.sendEvent(targetCtx, "IncomingDirectMessage", eventId, payload);
            boolean acked = false;
            if (sent) {
                try {
                    acked = waiter.get(1200, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                    acked = false;
                }
            }
            DeliveryTracker.getInstance().remove(eventId);
            if (acked) {
                wsDelivered++;
                continue;
            }

            for (PushTokenEntry token : tokens) {
                if (!targetCtx.getSessionId().equals(token.getSessionId())) continue;
                boolean pushed = FcmPushSender.sendNotification(token.getToken(), "Новое сообщение", text, messageId);
                if (pushed) {
                    fcmDelivered++;
                    break;
                }
            }
        }

        for (PushTokenEntry token : tokens) {
            if (activeSessionIds.contains(token.getSessionId())) continue;
            boolean pushed = FcmPushSender.sendNotification(token.getToken(), "Новое сообщение", text, messageId);
            if (pushed) fcmDelivered++;
        }

        Net_SendDirectMessage_Response resp = new Net_SendDirectMessage_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setMessageId(messageId);
        resp.setDeliveredWsSessions(wsDelivered);
        resp.setDeliveredFcmSessions(fcmDelivered);
        return resp;
    }

    private boolean canSend(String from, String to) {
        return from != null && !from.isBlank() && to != null && !to.isBlank();
    }
}
