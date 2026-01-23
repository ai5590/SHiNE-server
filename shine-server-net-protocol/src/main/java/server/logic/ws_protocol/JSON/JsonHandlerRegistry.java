package server.logic.ws_protocol.JSON;

import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;

import server.logic.ws_protocol.JSON.handlers.auth.Net_AuthChallenge_Handler;
import server.logic.ws_protocol.JSON.handlers.auth.Net_CloseActiveSession_Handler;
import server.logic.ws_protocol.JSON.handlers.auth.Net_CreateAuthSession__Handler;
import server.logic.ws_protocol.JSON.handlers.auth.Net_ListSessions_Handler;

// --- NEW v2 session login ---
import server.logic.ws_protocol.JSON.handlers.auth.Net_SessionChallenge_Handler;
import server.logic.ws_protocol.JSON.handlers.auth.Net_SessionLogin_Handler;

// --- auth entities ---
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_AuthChallenge_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_CloseActiveSession_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_CreateAuthSession_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_ListSessions_Request;

// --- NEW v2 entities ---
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_SessionChallenge_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_SessionLogin_Request;

import server.logic.ws_protocol.JSON.handlers.blockchain.Net_AddBlock_Handler;
import server.logic.ws_protocol.JSON.handlers.blockchain.entyties.Net_AddBlock_Request;

import server.logic.ws_protocol.JSON.handlers.tempToTest.Net_AddUser_Handler;
import server.logic.ws_protocol.JSON.handlers.tempToTest.entyties.Net_AddUser_Request;

import server.logic.ws_protocol.JSON.handlers.userParams.Net_GetUserParam_Handler;
import server.logic.ws_protocol.JSON.handlers.userParams.Net_ListUserParams_Handler;
import server.logic.ws_protocol.JSON.handlers.userParams.Net_UpsertUserParam_Handler;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_GetUserParam_Request;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_ListUserParams_Request;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_UpsertUserParam_Request;

// !!! подставь реальные пакеты/имена, как у тебя в проекте:
//import server.logic.ws_protocol.JSON.handlers.subscriptions.Net_GetSubscribedChannels_Handler;
import server.logic.ws_protocol.JSON.handlers.subscriptions.entyties.Net_GetSubscribedChannels_Request;

import java.util.Map;

/**
 * JsonHandlerRegistry — единое место, где руками регистрируются
 * JSON-операции: op → handler и op → requestClass.
 */
public final class JsonHandlerRegistry {

    // Map.of(...) поддерживает максимум 10 пар => используем Map.ofEntries(...)
    private static final Map<String, JsonMessageHandler> HANDLERS = Map.ofEntries(
            Map.entry("AddUser",            new Net_AddUser_Handler()),

            // --- auth ---
            Map.entry("AuthChallenge",      new Net_AuthChallenge_Handler()),
            Map.entry("CreateAuthSession",  new Net_CreateAuthSession__Handler()),
            Map.entry("CloseActiveSession", new Net_CloseActiveSession_Handler()),
            Map.entry("ListSessions",       new Net_ListSessions_Handler()),

            // --- login to existing session in 2 steps ---
            Map.entry("SessionChallenge",   new Net_SessionChallenge_Handler()),
            Map.entry("SessionLogin",       new Net_SessionLogin_Handler()),

            // --- blockchain ---
            Map.entry("AddBlock",           new Net_AddBlock_Handler()),

            // --- userParams ---
            Map.entry("UpsertUserParam",    new Net_UpsertUserParam_Handler()),
            Map.entry("GetUserParam",       new Net_GetUserParam_Handler()),
            Map.entry("ListUserParams",     new Net_ListUserParams_Handler())

            // --- subscriptions ---
//            Map.entry("ListSubscribedChannels", new Net_GetSubscribedChannels_Handler())
    );

    private static final Map<String, Class<? extends Net_Request>> REQUEST_TYPES = Map.ofEntries(
            Map.entry("AddUser",            Net_AddUser_Request.class),

            // --- auth ---
            Map.entry("AuthChallenge",      Net_AuthChallenge_Request.class),
            Map.entry("CreateAuthSession",  Net_CreateAuthSession_Request.class),
            Map.entry("CloseActiveSession", Net_CloseActiveSession_Request.class),
            Map.entry("ListSessions",       Net_ListSessions_Request.class),

            // --- NEW v2 ---
            Map.entry("SessionChallenge",   Net_SessionChallenge_Request.class),
            Map.entry("SessionLogin",       Net_SessionLogin_Request.class),

            // --- blockchain ---
            Map.entry("AddBlock",           Net_AddBlock_Request.class),

            // --- userParams ---
            Map.entry("UpsertUserParam",    Net_UpsertUserParam_Request.class),
            Map.entry("GetUserParam",       Net_GetUserParam_Request.class),
            Map.entry("ListUserParams",     Net_ListUserParams_Request.class),

            // --- subscriptions ---
            Map.entry("ListSubscribedChannels", Net_GetSubscribedChannels_Request.class)
    );

    private JsonHandlerRegistry() { }

    public static Map<String, JsonMessageHandler> getHandlers() {
        return HANDLERS;
    }

    public static Map<String, Class<? extends Net_Request>> getRequestTypes() {
        return REQUEST_TYPES;
    }
}