package server.logic.ws_protocol.JSON;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Реестр активных подключений (только авторизованные).
 */
public final class ActiveConnectionsRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActiveConnectionsRegistry.class);

    private static final ActiveConnectionsRegistry INSTANCE = new ActiveConnectionsRegistry();

    public static ActiveConnectionsRegistry getInstance() {
        return INSTANCE;
    }

    private ActiveConnectionsRegistry() {
        // singleton
    }

    // sessionId (String) -> ConnectionContext
    private final ConcurrentHashMap<String, ConnectionContext> bySessionId = new ConcurrentHashMap<>();

    // lowercase(login) -> множество ConnectionContext для этого пользователя
    private final ConcurrentHashMap<String, Set<ConnectionContext>> byLogin = new ConcurrentHashMap<>();

    /**
     * Зарегистрировать авторизованное подключение.
     * Ожидается, что в ctx уже выставлены login и sessionId.
     */
    public void register(ConnectionContext ctx) {
        if (ctx == null) return;

        String sessionId = ctx.getSessionId();
        String login = ctx.getLogin();

        if (sessionId == null || sessionId.isBlank() || login == null || login.isBlank()) {
            log.debug("register skipped: bad ctx fields (login='{}', sessionId='{}')", login, sessionId);
            return;
        }

        // ✅ Если кто-то перерегистрировал тот же sessionId — вычищаем старый ctx из byLogin
        ConnectionContext prev = bySessionId.put(sessionId, ctx);
        if (prev != null && prev != ctx) {
            String prevLogin = prev.getLogin();
            if (prevLogin != null && !prevLogin.isBlank()) {
                String prevKey = toLoginKey(prevLogin);
                Set<ConnectionContext> prevSet = byLogin.get(prevKey);
                if (prevSet != null) {
                    prevSet.remove(prev);
                    if (prevSet.isEmpty()) {
                        byLogin.remove(prevKey);
                    }
                }
            }
            log.warn("sessionId reused: replaced previous ctx (sessionId={}, prevLogin={}, newLogin={})",
                    sessionId, prevLogin, login);
        }

        byLogin
                .computeIfAbsent(toLoginKey(login), id -> new CopyOnWriteArraySet<>())
                .add(ctx);

        log.debug("registered ctx (login={}, sessionId={})", login, sessionId);
    }

    /**
     * Удалить подключение по контексту (например, при onClose).
     */
    public void remove(ConnectionContext ctx) {
        if (ctx == null) return;

        String sessionId = ctx.getSessionId();
        String login = ctx.getLogin();

        if (sessionId != null && !sessionId.isBlank()) {
            ConnectionContext removed = bySessionId.remove(sessionId);

            // Если в мапе лежал другой ctx под тем же sessionId — не трогаем его byLogin
            if (removed != null && removed != ctx) {
                log.debug("remove(ctx): sessionId mapped to another ctx, skip byLogin cleanup (sessionId={})", sessionId);
                return;
            }
        }

        if (login != null && !login.isBlank()) {
            String key = toLoginKey(login);
            Set<ConnectionContext> set = byLogin.get(key);
            if (set != null) {
                set.remove(ctx);
                if (set.isEmpty()) {
                    byLogin.remove(key);
                }
            }
        }

        log.debug("removed ctx (login={}, sessionId={})", login, sessionId);
    }

    /**
     * Удалить подключение по sessionId.
     */
    public void removeBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;

        ConnectionContext ctx = bySessionId.remove(sessionId);
        if (ctx == null) return;

        String login = ctx.getLogin();
        if (login != null && !login.isBlank()) {
            String key = toLoginKey(login);
            Set<ConnectionContext> set = byLogin.get(key);
            if (set != null) {
                set.remove(ctx);
                if (set.isEmpty()) {
                    byLogin.remove(key);
                }
            }
        }

        log.debug("removed by sessionId (login={}, sessionId={})", login, sessionId);
    }

    /**
     * Получить контекст по sessionId.
     */
    public ConnectionContext getBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        return bySessionId.get(sessionId);
    }

    /**
     * Получить все активные подключения пользователя по login.
     */
    public Set<ConnectionContext> getByLogin(String login) {
        if (login == null || login.isBlank()) return Set.of();
        Set<ConnectionContext> set = byLogin.get(toLoginKey(login));
        return (set == null) ? Set.of() : set; // CopyOnWriteArraySet можно отдавать как есть
    }

    private static String toLoginKey(String login) {
        return login.trim().toLowerCase(Locale.ROOT);
    }
}
