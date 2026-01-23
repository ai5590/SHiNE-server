//package server.logic.ws_protocol.JSON.handlers.auth.entyties;
//
//import server.logic.ws_protocol.JSON.entyties.Net_Response;
//
///**
// * Ответ на RefreshSession.
// *
// * В новой версии (v2) RefreshSession ОТКЛЮЧЕН.
// * Этот класс можно оставить временно для совместимости сериализации,
// * но handler будет возвращать 410 GONE.
// */
//public class Net_RefreshSession_Response extends Net_Response {
//
//    private String storagePwd;
//
//    public String getStoragePwd() {
//        return storagePwd;
//    }
//
//    public void setStoragePwd(String storagePwd) {
//        this.storagePwd = storagePwd;
//    }
//}