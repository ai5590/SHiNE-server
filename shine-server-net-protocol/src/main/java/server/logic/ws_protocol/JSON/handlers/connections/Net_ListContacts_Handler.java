package server.logic.ws_protocol.JSON.handlers.connections;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_ListContacts_Request;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_ListContacts_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.MsgSubType;
import shine.db.dao.ConnectionsStateDAO;

import java.sql.Connection;
import java.util.List;

public class Net_ListContacts_Handler implements JsonMessageHandler {
    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_ListContacts_Request req = (Net_ListContacts_Request) baseRequest;
        if (ctx == null || !ctx.isAuthenticatedUser()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NOT_AUTHENTICATED", "Требуется авторизация");
        }

        try (Connection c = shine.db.SqliteDbController.getInstance().getConnection()) {
            List<String> contacts = ConnectionsStateDAO.getInstance().listOutgoingByRelTypeCanonical(c, ctx.getLogin(), MsgSubType.CONNECTION_CONTACT);
            Net_ListContacts_Response resp = new Net_ListContacts_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);
            resp.setLogin(ctx.getLogin());
            resp.setContacts(contacts);
            return resp;
        }
    }
}
