package server.logic.ws_protocol.JSON;

import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_AuthChallenge_Request;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_CreateAuthSession_Request;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_RefreshSession_Request;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_CloseActiveSession_Request;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_ListSessions_Request;
import server.logic.ws_protocol.JSON.entyties.tempToTest.Net_AddUser_Request;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.auth.Net_AuthChallenge_Handler;
import server.logic.ws_protocol.JSON.handlers.auth.Net_CreateAuthSession__Handler;
import server.logic.ws_protocol.JSON.handlers.auth.Net_RefreshSession_Handler;
import server.logic.ws_protocol.JSON.handlers.auth.Net_CloseActiveSession_Handler;
import server.logic.ws_protocol.JSON.handlers.auth.Net_ListSessions_Handler;
import server.logic.ws_protocol.JSON.handlers.tempToTest.Net_AddUser_Handler;

import java.util.Map;

/**
 * JsonHandlerRegistry — единое место, где руками регистрируются
 * JSON-операции: op → handler и op → requestClass.
 *
 * Если нужно добавить новый запрос:
 *   1) создаёшь класс NetXXXRequest / NetXXXResponse,
 *   2) создаёшь JsonMessageHandler (NetXXXHandler),
 *   3) добавляешь op в HANDLERS и REQUEST_TYPES.
 */
public final class JsonHandlerRegistry {

    private static final Map<String, JsonMessageHandler> HANDLERS = Map.of(
            "RefreshSession",     new Net_RefreshSession_Handler(),
            "AddUser",            new Net_AddUser_Handler(),
            "AuthChallenge",      new Net_AuthChallenge_Handler(),
            "CreateAuthSession",  new Net_CreateAuthSession__Handler(),
            "CloseActiveSession", new Net_CloseActiveSession_Handler(),
            "ListSessions",       new Net_ListSessions_Handler()
            // сюда потом добавишь другие операции
    );

    private static final Map<String, Class<? extends Net_Request>> REQUEST_TYPES = Map.of(
            "RefreshSession",     Net_RefreshSession_Request.class,
            "AddUser",            Net_AddUser_Request.class,
            "AuthChallenge",      Net_AuthChallenge_Request.class,
            "CreateAuthSession",  Net_CreateAuthSession_Request.class,
            "CloseActiveSession", Net_CloseActiveSession_Request.class,
            "ListSessions",       Net_ListSessions_Request.class
    );

    private JsonHandlerRegistry() {
        // utility
    }

    public static Map<String, JsonMessageHandler> getHandlers() {
        return HANDLERS;
    }

    public static Map<String, Class<? extends Net_Request>> getRequestTypes() {
        return REQUEST_TYPES;
    }
}