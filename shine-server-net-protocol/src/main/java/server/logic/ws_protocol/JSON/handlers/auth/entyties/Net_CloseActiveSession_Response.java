package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Ответ на CloseActiveSession.
 *
 * При успехе:
 *  - status = 200;
 *  - payload = {}.
 *
 * Закрытие WebSocket-соединения может быть выполнено сразу (для другой сессии)
 * или чуть позже (для текущей сессии) после отправки ответа.
 */
public class Net_CloseActiveSession_Response extends Net_Response {
    // Дополнительных полей пока не требуется.
}