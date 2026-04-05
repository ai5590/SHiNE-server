package server.logic.ws_protocol.JSON.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;

public final class WsEventSender {
    private static final Logger log = LoggerFactory.getLogger(WsEventSender.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WsEventSender() {}

    public static boolean sendEvent(ConnectionContext ctx, String op, String eventId, ObjectNode payload) {
        if (ctx == null) return false;
        Session session = ctx.getWsSession();
        if (session == null || !session.isOpen()) return false;
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("op", op);
            root.put("requestId", eventId);
            root.put("status", 200);
            root.put("ok", true);
            root.put("event", true);
            root.set("payload", payload == null ? MAPPER.createObjectNode() : payload);
            session.getRemote().sendString(root.toString());
            return true;
        } catch (Exception e) {
            log.warn("Failed to send ws event op={} sessionId={}", op, ctx.getSessionId(), e);
            return false;
        }
    }
}
