package server.ws;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import shine.db.entities.SolanaUserEntry;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Утилита для работы с WebSocket-подключениями.
 *
 * Цель этой версии:
 * - всегда логировать "кто закрыл" / "что закрывали" / "в каком состоянии был WS";
 * - логировать исключения так, чтобы было видно первопричину;
 * - не терять контекст из-за ctx.reset() (сначала снимаем "снимок" полей).
 */
public final class WsConnectionUtils {

    private static final Logger log = LoggerFactory.getLogger(WsConnectionUtils.class);

    /** Счётчик событий закрытия (удобно коррелировать логи). */
    private static final AtomicLong CLOSE_SEQ = new AtomicLong(0);

    private WsConnectionUtils() {
        // utility
    }

    public static void closeConnection(ConnectionContext ctx, int statusCode, String reason) {
        closeConnection(ctx, statusCode, reason, null, "UNKNOWN");
    }

    /**
     * Расширенное закрытие с указанием инициатора и причины (Throwable).
     *
     * @param ctx         контекст
     * @param statusCode  код закрытия
     * @param reason      причина (пойдёт в close frame + логи)
     * @param cause       исключение/первопричина (если закрываем из catch)
     * @param initiator   строка "кто инициировал" (handler/op/requestId/etc.)
     */
    public static void closeConnection(ConnectionContext ctx,
                                       int statusCode,
                                       String reason,
                                       Throwable cause,
                                       String initiator) {
        if (ctx == null) return;

        final long closeId = CLOSE_SEQ.incrementAndGet();

        // --- СНИМОК КОНТЕКСТА ДО reset() ---
        final Session ws = ctx.getWsSession();

        final String sessionId = safeString(ctx.getSessionId());
        final int authStatus = safeAuthStatus(ctx);

        final SolanaUserEntry user = ctx.getSolanaUser();
        final String login = (user != null ? safeString(user.getLogin()) : "");

        final String activeSessionId =
                (ctx.getActiveSession() != null ? safeString(ctx.getActiveSession().getSessionId()) : "");

        final boolean wsPresent = (ws != null);
        final boolean wsOpen = (ws != null && safeIsOpen(ws));
        final String wsInfo = formatWsInfo(ws);

        final String threadName = Thread.currentThread().getName();
        final int ctxId = System.identityHashCode(ctx);

        // Логируем "начало закрытия" всегда, чтобы видеть даже случаи "ws уже закрыт"
        if (cause != null) {
            log.warn("WS_CLOSE#{} BEGIN initiator={} thread={} ctxId={} login={} sessionId={} activeSessionId={} authStatus={} statusCode={} reason={} wsPresent={} wsOpen={} wsInfo={}",
                    closeId, initiator, threadName, ctxId, login, sessionId, activeSessionId, authStatus, statusCode, reason, wsPresent, wsOpen, wsInfo, cause);
        } else {
            log.info("WS_CLOSE#{} BEGIN initiator={} thread={} ctxId={} login={} sessionId={} activeSessionId={} authStatus={} statusCode={} reason={} wsPresent={} wsOpen={} wsInfo={}",
                    closeId, initiator, threadName, ctxId, login, sessionId, activeSessionId, authStatus, statusCode, reason, wsPresent, wsOpen, wsInfo);
        }

        // --- ШАГ 1: убрать из реестра (чтобы новые сообщения не шли в мёртвый контекст) ---
        try {
            ActiveConnectionsRegistry.getInstance().remove(ctx);
            log.debug("WS_CLOSE#{} registry.remove OK ctxId={} sessionId={} login={}", closeId, ctxId, sessionId, login);
        } catch (Exception e) {
            log.warn("WS_CLOSE#{} registry.remove FAIL ctxId={} sessionId={} login={}", closeId, ctxId, sessionId, login, e);
        }

        // --- ШАГ 2: закрыть WS (если открыт) ---
        if (ws != null) {
            if (safeIsOpen(ws)) {
                try {
                    ws.close(statusCode, safeString(reason));
                    log.info("WS_CLOSE#{} ws.close OK ctxId={} sessionId={} login={} statusCode={} reason={}",
                            closeId, ctxId, sessionId, login, statusCode, reason);
                } catch (Exception e) {
                    log.warn("WS_CLOSE#{} ws.close FAIL ctxId={} sessionId={} login={} statusCode={} reason={} wsInfo={}",
                            closeId, ctxId, sessionId, login, statusCode, reason, wsInfo, e);
                }
            } else {
                log.info("WS_CLOSE#{} ws already closed ctxId={} sessionId={} login={} wsInfo={}",
                        closeId, ctxId, sessionId, login, wsInfo);
            }
        }

        // --- ШАГ 3: очистить контекст (в конце, чтобы не потерять поля в логах выше) ---
        try {
            ctx.reset();
            log.debug("WS_CLOSE#{} ctx.reset OK ctxId={} (was sessionId={}, login={})", closeId, ctxId, sessionId, login);
        } catch (Exception e) {
            log.warn("WS_CLOSE#{} ctx.reset FAIL ctxId={} (was sessionId={}, login={})", closeId, ctxId, sessionId, login, e);
        }

        log.info("WS_CLOSE#{} END initiator={} ctxId={} sessionId={} login={}", closeId, initiator, ctxId, sessionId, login);
    }

    private static String safeString(String s) {
        return (s == null ? "" : s);
    }

    private static int safeAuthStatus(ConnectionContext ctx) {
        try {
            return ctx.getAuthenticationStatus();
        } catch (Exception e) {
            return -999;
        }
    }

    private static boolean safeIsOpen(Session ws) {
        try {
            return ws.isOpen();
        } catch (Exception e) {
            return false;
        }
    }

    private static String formatWsInfo(Session ws) {
        if (ws == null) return "null";

        String remote = "";
        String local = "";
        try {
            SocketAddress ra = ws.getRemoteAddress();
            remote = (ra != null ? ra.toString() : "");
        } catch (Exception ignored) { }

        try {
            SocketAddress la = ws.getLocalAddress();
            local = (la != null ? la.toString() : "");
        } catch (Exception ignored) { }

        return "remote=" + remote + ", local=" + local;
    }
}