package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.List;

/**
 * Ответ на ListSessions.
 *
 * При успехе:
 *  - status = 200;
 *  - payload:
 *    {
 *      "sessions": [
 *        {
 *          "sessionId": "...",
 *          "clientInfoFromClient": "...",
 *          "clientInfoFromRequest": "...",
 *          "geo": "Country, City" | "unknown",
 *          "lastAuthirificatedAtMs": 1733310000000
 *        },
 *        ...
 *      ]
 *    }
 */
public class Net_ListSessions_Response extends Net_Response {

    /**
     * Список активных сессий для текущего пользователя.
     */
    private List<SessionInfo> sessions;

    public List<SessionInfo> getSessions() {
        return sessions;
    }

    public void setSessions(List<SessionInfo> sessions) {
        this.sessions = sessions;
    }

    /**
     * Описание одной активной сессии.
     */
    public static class SessionInfo {

        /** Идентификатор сессии, base64 от 32 байт. */
        private String sessionId;

        /** Что прислал клиент в CreateAuthSession/RefreshSession (clientInfo). */
        private String clientInfoFromClient;

        /** Краткая строка, собранная сервером из HTTP-запроса (UA, платформа и т.п.). */
        private String clientInfoFromRequest;

        /** Строка геолокации вида "Country, City" или "unknown". */
        private String geo;

        /** Время последней успешной авторизации/refresh (мс с 1970-01-01). */
        private long lastAuthirificatedAtMs;

        // --- getters / setters ---

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getClientInfoFromClient() {
            return clientInfoFromClient;
        }

        public void setClientInfoFromClient(String clientInfoFromClient) {
            this.clientInfoFromClient = clientInfoFromClient;
        }

        public String getClientInfoFromRequest() {
            return clientInfoFromRequest;
        }

        public void setClientInfoFromRequest(String clientInfoFromRequest) {
            this.clientInfoFromRequest = clientInfoFromRequest;
        }

        public String getGeo() {
            return geo;
        }

        public void setGeo(String geo) {
            this.geo = geo;
        }

        public long getLastAuthirificatedAtMs() {
            return lastAuthirificatedAtMs;
        }

        public void setLastAuthirificatedAtMs(long lastAuthirificatedAtMs) {
            this.lastAuthirificatedAtMs = lastAuthirificatedAtMs;
        }
    }
}