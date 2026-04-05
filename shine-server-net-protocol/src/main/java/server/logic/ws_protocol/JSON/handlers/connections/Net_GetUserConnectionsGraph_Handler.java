package server.logic.ws_protocol.JSON.handlers.connections;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_GetUserConnectionsGraph_Request;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_GetUserConnectionsGraph_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.MsgSubType;
import shine.db.dao.ConnectionsStateDAO;

import java.sql.Connection;
import java.util.List;

public class Net_GetUserConnectionsGraph_Handler implements JsonMessageHandler {
    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_GetUserConnectionsGraph_Request req = (Net_GetUserConnectionsGraph_Request) baseRequest;
        if (ctx == null || !ctx.isAuthenticatedUser()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NOT_AUTHENTICATED", "Требуется авторизация");
        }
        String login = (req.getLogin() == null || req.getLogin().isBlank()) ? ctx.getLogin() : req.getLogin().trim();

        try (Connection c = shine.db.SqliteDbController.getInstance().getConnection()) {
            List<String> out = ConnectionsStateDAO.getInstance().listOutgoingByRelTypeCanonical(c, login, MsgSubType.CONNECTION_FRIEND);
            List<String> in = ConnectionsStateDAO.getInstance().listIncomingByRelTypeCanonical(c, login, MsgSubType.CONNECTION_FRIEND);

            Net_GetUserConnectionsGraph_Response resp = new Net_GetUserConnectionsGraph_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);
            resp.setLogin(login);
            resp.setOutFriends(out);
            resp.setInFriends(in);
            return resp;
        }
    }
}
