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

import server.logic.ws_protocol.JSON.handlers.tempToTest.Net_GetUser_Handler;
import server.logic.ws_protocol.JSON.handlers.tempToTest.entyties.Net_GetUser_Request;

// --- NEW: SearchUsers ---
import server.logic.ws_protocol.JSON.handlers.tempToTest.Net_SearchUsers_Handler;
import server.logic.ws_protocol.JSON.handlers.tempToTest.entyties.Net_SearchUsers_Request;

import server.logic.ws_protocol.JSON.handlers.userParams.Net_GetUserParam_Handler;
import server.logic.ws_protocol.JSON.handlers.userParams.Net_ListUserParams_Handler;
import server.logic.ws_protocol.JSON.handlers.userParams.Net_UpsertUserParam_Handler;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_GetUserParam_Request;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_ListUserParams_Request;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_UpsertUserParam_Request;

// --- NEW: connections friends lists ---
import server.logic.ws_protocol.JSON.handlers.connections.Net_GetFriendsLists_Handler;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_GetFriendsLists_Request;
import server.logic.ws_protocol.JSON.handlers.channels.Net_GetChannelMessages_Handler;
import server.logic.ws_protocol.JSON.handlers.channels.Net_GetMessageThread_Handler;
import server.logic.ws_protocol.JSON.handlers.channels.Net_ListSubscriptionsFeed_Handler;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_GetChannelMessages_Request;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_GetMessageThread_Request;
import server.logic.ws_protocol.JSON.handlers.channels.entyties.Net_ListSubscriptionsFeed_Request;
import server.logic.ws_protocol.JSON.handlers.connections.Net_GetUserConnectionsGraph_Handler;
import server.logic.ws_protocol.JSON.handlers.connections.Net_AddCloseFriend_Handler;
import server.logic.ws_protocol.JSON.handlers.connections.Net_ListContacts_Handler;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_GetUserConnectionsGraph_Request;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_AddCloseFriend_Request;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_ListContacts_Request;
import server.logic.ws_protocol.JSON.messages.Net_AckIncomingMessage_Handler;
import server.logic.ws_protocol.JSON.messages.Net_SendDirectMessage_Handler;
import server.logic.ws_protocol.JSON.messages.Net_UpsertPushToken_Handler;
import server.logic.ws_protocol.JSON.messages.entyties.Net_AckIncomingMessage_Request;
import server.logic.ws_protocol.JSON.messages.entyties.Net_SendDirectMessage_Request;
import server.logic.ws_protocol.JSON.messages.entyties.Net_UpsertPushToken_Request;

// --- NEW: Ping ---
import server.logic.ws_protocol.JSON.handlers.system.Net_GetServerInfo_Handler;
import server.logic.ws_protocol.JSON.handlers.system.Net_ClientErrorLog_Handler;
import server.logic.ws_protocol.JSON.handlers.system.Net_Ping_Handler;
import server.logic.ws_protocol.JSON.handlers.system.entyties.Net_ClientErrorLog_Request;
import server.logic.ws_protocol.JSON.handlers.system.entyties.Net_GetServerInfo_Request;
import server.logic.ws_protocol.JSON.handlers.system.entyties.Net_Ping_Request;

import java.util.Map;

/**
 * JsonHandlerRegistry — единое место, где руками регистрируются
 * JSON-операции: op → handler и op → requestClass.
 */
public final class JsonHandlerRegistry {

    private static final Map<String, JsonMessageHandler> HANDLERS = Map.ofEntries(
            Map.entry("AddUser",            new Net_AddUser_Handler()),
            Map.entry("GetUser",            new Net_GetUser_Handler()),
            Map.entry("SearchUsers",        new Net_SearchUsers_Handler()),

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
            Map.entry("ListUserParams",     new Net_ListUserParams_Handler()),

            // --- connections ---
            Map.entry("GetFriendsLists",    new Net_GetFriendsLists_Handler()),
            Map.entry("ListSubscriptionsFeed", new Net_ListSubscriptionsFeed_Handler()),
            Map.entry("GetChannelMessages", new Net_GetChannelMessages_Handler()),
            Map.entry("GetMessageThread", new Net_GetMessageThread_Handler()),
            Map.entry("ListContacts", new Net_ListContacts_Handler()),
            Map.entry("GetUserConnectionsGraph", new Net_GetUserConnectionsGraph_Handler()),
            Map.entry("AddCloseFriend", new Net_AddCloseFriend_Handler()),

            // --- direct messages / push ---
            Map.entry("UpsertPushToken", new Net_UpsertPushToken_Handler()),
            Map.entry("SendDirectMessage", new Net_SendDirectMessage_Handler()),
            Map.entry("AckIncomingMessage", new Net_AckIncomingMessage_Handler()),

            // --- system ---
            Map.entry("Ping",               new Net_Ping_Handler()),
            Map.entry("GetServerInfo",      new Net_GetServerInfo_Handler()),
            Map.entry("ClientErrorLog",     new Net_ClientErrorLog_Handler())

            // --- subscriptions ---
//            Map.entry("ListSubscribedChannels", new Net_GetSubscribedChannels_Handler())
    );

    private static final Map<String, Class<? extends Net_Request>> REQUEST_TYPES = Map.ofEntries(
            Map.entry("AddUser",            Net_AddUser_Request.class),
            Map.entry("GetUser",            Net_GetUser_Request.class),
            Map.entry("SearchUsers",        Net_SearchUsers_Request.class),

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

            // --- connections ---
            Map.entry("GetFriendsLists",    Net_GetFriendsLists_Request.class),
            Map.entry("ListSubscriptionsFeed", Net_ListSubscriptionsFeed_Request.class),
            Map.entry("GetChannelMessages", Net_GetChannelMessages_Request.class),
            Map.entry("GetMessageThread", Net_GetMessageThread_Request.class),
            Map.entry("ListContacts", Net_ListContacts_Request.class),
            Map.entry("GetUserConnectionsGraph", Net_GetUserConnectionsGraph_Request.class),
            Map.entry("AddCloseFriend", Net_AddCloseFriend_Request.class),

            // --- direct messages / push ---
            Map.entry("UpsertPushToken", Net_UpsertPushToken_Request.class),
            Map.entry("SendDirectMessage", Net_SendDirectMessage_Request.class),
            Map.entry("AckIncomingMessage", Net_AckIncomingMessage_Request.class),

            // --- system ---
            Map.entry("Ping",               Net_Ping_Request.class),
            Map.entry("GetServerInfo",      Net_GetServerInfo_Request.class),
            Map.entry("ClientErrorLog",     Net_ClientErrorLog_Request.class)
    );

    private JsonHandlerRegistry() { }

    public static Map<String, JsonMessageHandler> getHandlers() {
        return HANDLERS;
    }

    public static Map<String, Class<? extends Net_Request>> getRequestTypes() {
        return REQUEST_TYPES;
    }
}
