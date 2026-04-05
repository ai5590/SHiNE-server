package server.logic.ws_protocol.JSON.handlers.connections;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_AddCloseFriend_Request;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_AddCloseFriend_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.MsgSubType;
import shine.db.dao.ConnectionsStateDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Net_AddCloseFriend_Handler implements JsonMessageHandler {
    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_AddCloseFriend_Request req = (Net_AddCloseFriend_Request) baseRequest;
        if (ctx == null || !ctx.isAuthenticatedUser()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.UNVERIFIED, "NOT_AUTHENTICATED", "Требуется авторизация");
        }
        if (req.getToLogin() == null || req.getToLogin().isBlank()) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_FIELDS", "toLogin обязателен");
        }

        String from = ctx.getLogin();
        String toLogin = req.getToLogin().trim();
        if (from.equalsIgnoreCase(toLogin)) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_FIELDS", "Нельзя добавить себя");
        }

        try (Connection c = shine.db.SqliteDbController.getInstance().getConnection()) {
            String canonicalTo = findCanonicalLogin(c, toLogin);
            if (canonicalTo == null) {
                return NetExceptionResponseFactory.error(req, 404, "USER_NOT_FOUND", "Пользователь не найден");
            }
            String targetBch = findPrimaryBlockchain(c, canonicalTo);
            if (targetBch == null) {
                return NetExceptionResponseFactory.error(req, 404, "BLOCKCHAIN_NOT_FOUND", "У пользователя нет blockchain");
            }

            ConnectionsStateDAO.getInstance().upsertRelation(
                    c,
                    from,
                    MsgSubType.CONNECTION_FRIEND,
                    canonicalTo,
                    targetBch,
                    0,
                    new byte[32]
            );

            Net_AddCloseFriend_Response resp = new Net_AddCloseFriend_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);
            resp.setLogin(from);
            resp.setToLogin(canonicalTo);
            resp.setRelation("close_friend");
            return resp;
        }
    }

    private String findCanonicalLogin(Connection c, String login) throws Exception {
        String sql = "SELECT login FROM solana_users WHERE login = ? COLLATE NOCASE LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("login") : null;
            }
        }
    }

    private String findPrimaryBlockchain(Connection c, String login) throws Exception {
        String sql = "SELECT blockchain_name FROM blockchain_state WHERE login=? ORDER BY blockchain_name LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("blockchain_name") : null;
            }
        }
    }
}
