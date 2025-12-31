package server.logic.ws_protocol.JSON.handlers.tempToTest.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Успешный ответ на AddUser.
 *
 * Сейчас дополнительных полей нет — достаточно status=200.
 *
 * Пример:
 * {
 *   "op": "AddUser",
 *   "requestId": "test-add-1",
 *   "status": 200,
 *   "payload": { }
 * }
 */
public class Net_AddUser_Response extends Net_Response {
    // При необходимости сюда можно добавить, например, флаг created/updated и т.п.
}
