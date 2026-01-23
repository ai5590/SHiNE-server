//package server.logic.ws_protocol.JSON.handlers.auth;
//
//import server.logic.ws_protocol.JSON.ConnectionContext;
//import server.logic.ws_protocol.JSON.entyties.Net_Request;
//import server.logic.ws_protocol.JSON.entyties.Net_Response;
//import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
//import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_RefreshSession_Request;
//import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
//import server.logic.ws_protocol.WireCodes;
//
///**
// * RefreshSession (v2) — ОТКЛЮЧЕН.
// *
// * Раньше это был "короткий вход" (1 запрос) по sessionId+sessionPwd.
// * Теперь вход всегда 2 шага: SessionChallenge -> SessionLogin (подпись sessionKey).
// */
//public class Net_RefreshSession_Handler implements JsonMessageHandler {
//
//    @Override
//    public Net_Response handle(Net_Request request, ConnectionContext ctx) throws Exception {
//        Net_RefreshSession_Request req = (Net_RefreshSession_Request) request;
//
//        return NetExceptionResponseFactory.error(
//                req,
//                WireCodes.Status.GONE,          // 410
//                "DISABLED_V2",
//                "RefreshSession отключён в v2. Используй SessionChallenge + SessionLogin."
//        );
//    }
//}