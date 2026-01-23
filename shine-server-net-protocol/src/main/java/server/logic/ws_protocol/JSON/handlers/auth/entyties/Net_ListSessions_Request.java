package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос ListSessions — список активных сессий пользователя.
 *
 * Новая логика (v2):
 * - Доступно ТОЛЬКО после успешного входа в сессию (AUTH_STATUS_USER).
 * - Пустой payload.
 */
public class Net_ListSessions_Request extends Net_Request {
    // пусто
}