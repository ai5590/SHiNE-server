//package server.logic.ws_protocol.JSON.handlers.auth.entyties;
//
//import server.logic.ws_protocol.JSON.entyties.Net_Request;
//
///**
// * Запрос RefreshSession.
// *
// * В новой версии (v2) RefreshSession ОТКЛЮЧЕН.
// * Оставлен временно для совместимости, handler вернёт 410 GONE.
// */
//public class Net_RefreshSession_Request extends Net_Request {
//
//    private String sessionId;
//    private String sessionPwd;
//    private String clientInfo;
//
//    public String getSessionId() {
//        return sessionId;
//    }
//
//    public void setSessionId(String sessionId) {
//        this.sessionId = sessionId;
//    }
//
//    public String getSessionPwd() {
//        return sessionPwd;
//    }
//
//    public void setSessionPwd(String sessionPwd) {
//        this.sessionPwd = sessionPwd;
//    }
//
//    public String getClientInfo() { return clientInfo; }
//
//    public void setClientInfo(String clientInfo) { this.clientInfo = clientInfo; }
//}