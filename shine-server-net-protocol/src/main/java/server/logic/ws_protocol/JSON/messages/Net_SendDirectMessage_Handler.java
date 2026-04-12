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
import server.logic.ws_protocol.JSON.push.WebPushSender;
import server.logic.ws_protocol.JSON.push.WsEventSender;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.JSON.utils.NetIdGenerator;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.dao.DirectMessagesDAO;
import shine.db.dao.SignedDirectMessagesHistoryDAO;
import shine.db.dao.SignedDmReplayDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.ActiveSessionEntry;
import shine.db.entities.DirectMessageEntry;
import shine.db.entities.SignedDirectMessageHistoryEntry;
import shine.db.entities.SolanaUserEntry;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Net_SendDirectMessage_Handler implements JsonMessageHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long REPLAY_TTL_MS = 15L * 60L * 1000L;
    private static final int MAX_MESSAGE_BYTES = 3000;

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_SendDirectMessage_Request req = (Net_SendDirectMessage_Request) baseRequest;
        if (req.getBlobB64() == null || req.getBlobB64().isBlank()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_FIELDS", "blobB64 обязателен");
        }

        final byte[] raw;
        final SignedDirectMessagePacket packet;
        try {
            raw = Base64.getDecoder().decode(req.getBlobB64().trim());
            packet = SignedDirectMessagePacket.parse(raw, MAX_MESSAGE_BYTES);
        } catch (IllegalArgumentException ex) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, ex.getMessage(), "Некорректный формат пакета");
        }

        SolanaUserEntry fromUser = SolanaUsersDAO.getInstance().getByLogin(packet.fromLogin);
        SolanaUserEntry toUser = SolanaUsersDAO.getInstance().getByLogin(packet.toLogin);
        if (fromUser == null || toUser == null) {
            return NetExceptionResponseFactory.error(req, 404, "USER_NOT_FOUND", "from/to пользователь не найден");
        }

        byte[] publicKey32;
        try {
            publicKey32 = Ed25519Util.keyFromBase64(fromUser.getDeviceKey());
        } catch (Exception e) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNPROCESSABLE, "BAD_DEVICE_KEY", "Некорректный deviceKey отправителя");
        }
        if (!Ed25519Util.verify(packet.signedBody, packet.signature64, publicKey32)) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNPROCESSABLE, "BAD_SIGNATURE", "Подпись не прошла проверку");
        }

        long now = System.currentTimeMillis();
        if (Math.abs(now - packet.timeMs) > REPLAY_TTL_MS) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNPROCESSABLE, "BAD_TIME_WINDOW", "Время сообщения вышло за окно 15 минут");
        }

        boolean replayOk = SignedDmReplayDAO.getInstance().registerUnique(packet.fromLogin, packet.timeMs, packet.nonce, now);
        if (!replayOk) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNPROCESSABLE, "REPLAY", "Повторное сообщение заблокировано");
        }

        String messageId = NetIdGenerator.eventId("msg");
        String textForUi = new String(packet.messageBytes, StandardCharsets.UTF_8);

        DirectMessageEntry entry = new DirectMessageEntry();
        entry.setMessageId(messageId);
        entry.setFromLogin(packet.fromLogin);
        entry.setToLogin(packet.toLogin);
        entry.setText(textForUi);
        entry.setCreatedAtMs(now);
        DirectMessagesDAO.getInstance().insert(entry);

        SignedDirectMessageHistoryEntry history = new SignedDirectMessageHistoryEntry();
        history.setMessageId(messageId);
        history.setFromLogin(packet.fromLogin);
        history.setToLogin(packet.toLogin);
        history.setTargetMode(packet.targetMode);
        history.setTargetSessionId(packet.targetSessionId);
        history.setMessageType(packet.messageType);
        history.setTimeMs(packet.timeMs);
        history.setNonce(packet.nonce);
        history.setRawPacket(packet.rawPacket);
        history.setCreatedAtMs(now);
        SignedDirectMessagesHistoryDAO.getInstance().insert(history);

        DeliveryResult delivery = deliver(packet, req.getBlobB64().trim(), messageId, now);

        Net_SendDirectMessage_Response resp = new Net_SendDirectMessage_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setMessageId(messageId);
        resp.setDeliveredWsSessions(delivery.wsDelivered);
        resp.setDeliveredWebPushSessions(delivery.webPushDelivered);
        resp.setSessionNotFound(delivery.sessionNotFound);
        return resp;
    }

    private DeliveryResult deliver(SignedDirectMessagePacket packet, String blobB64, String messageId, long createdAtMs) throws Exception {
        DeliveryResult result = new DeliveryResult();

        Set<String> selectedSessionIds = new HashSet<>();
        if (packet.targetMode == SignedDirectMessagePacket.TARGET_ONE_SESSION) {
            ActiveSessionEntry byId = ActiveSessionsDAO.getInstance().getBySessionId(packet.targetSessionId);
            if (byId == null || !packet.toLogin.equalsIgnoreCase(byId.getLogin())) {
                result.sessionNotFound = true;
                return result;
            }
            selectedSessionIds.add(byId.getSessionId());
            deliverToSession(packet, blobB64, messageId, createdAtMs, byId.getSessionId(), result);
            return result;
        }

        List<ActiveSessionEntry> sessions = ActiveSessionsDAO.getInstance().getByLogin(packet.toLogin);
        for (ActiveSessionEntry s : sessions) {
            selectedSessionIds.add(s.getSessionId());
            deliverToSession(packet, blobB64, messageId, createdAtMs, s.getSessionId(), result);
        }
        return result;
    }

    private void deliverToSession(
            SignedDirectMessagePacket packet,
            String blobB64,
            String messageId,
            long createdAtMs,
            String sessionId,
            DeliveryResult result
    ) {
        ConnectionContext targetCtx = ActiveConnectionsRegistry.getInstance().getBySessionId(sessionId);
        boolean wsDelivered = false;
        if (targetCtx != null) {
            String eventId = NetIdGenerator.eventId("evt");
            CompletableFuture<Boolean> waiter = DeliveryTracker.getInstance().register(eventId);
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("eventId", eventId);
            payload.put("messageId", messageId);
            payload.put("fromLogin", packet.fromLogin);
            payload.put("toLogin", packet.toLogin);
            payload.put("blobB64", blobB64);
            payload.put("text", new String(packet.messageBytes, StandardCharsets.UTF_8));
            payload.put("timeMs", createdAtMs);

            boolean sent = WsEventSender.sendEvent(targetCtx, "IncomingDirectMessage", eventId, payload);
            if (sent) {
                try {
                    wsDelivered = waiter.get(1200, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                    wsDelivered = false;
                }
            }
            DeliveryTracker.getInstance().remove(eventId);
        }

        if (wsDelivered) {
            result.wsDelivered++;
            return;
        }

        try {
            ActiveSessionEntry targetSession = ActiveSessionsDAO.getInstance().getBySessionId(sessionId);
            if (targetSession == null) return;
            if (isBlank(targetSession.getPushEndpoint()) || isBlank(targetSession.getPushP256dhKey()) || isBlank(targetSession.getPushAuthKey())) {
                return;
            }
            boolean pushed = WebPushSender.sendBase64Payload(
                    targetSession.getPushEndpoint(),
                    targetSession.getPushP256dhKey(),
                    targetSession.getPushAuthKey(),
                    blobB64
            );
            if (pushed) result.webPushDelivered++;
        } catch (Exception ignored) {
            // ignore per-session push errors
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static final class DeliveryResult {
        int wsDelivered;
        int webPushDelivered;
        boolean sessionNotFound;
    }
}
