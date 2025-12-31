package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Успешный ответ на RefreshSession.
 *
 * Дополнительно к статусу 200 сервер возвращает storagePwd,
 * чтобы клиент мог восстановить/синхронизировать локальное хранилище.
 *
 * JSON:
 * {
 *   "op": "RefreshSession",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "storagePwd": "base64-строка-от-32-байт"
 *   }
 * }
 */
public class Net_RefreshSession_Response extends Net_Response {

    /** Пароль хранилища, сохранённый в сессии (storagePwd). */
    private String storagePwd;

    public String getStoragePwd() {
        return storagePwd;
    }

    public void setStoragePwd(String storagePwd) {
        this.storagePwd = storagePwd;
    }
}
