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

            // Idempotent insert for close-friend relation.
            // Using INSERT OR IGNORE avoids ON CONFLICT(column list) mismatches
            // across DB instances with different UNIQUE schemas.
            insertCloseFriendIgnoreDuplicate(c, from, canonicalTo, targetBch);

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
        String sql = "SELECT blockchain_name FROM blockchain_state WHERE login = ? COLLATE NOCASE ORDER BY blockchain_name LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("blockchain_name") : null;
            }
        }
    }

    private void insertCloseFriendIgnoreDuplicate(Connection c,
                                                  String login,
                                                  String toLogin,
                                                  String toBchName) throws Exception {
        String sql = """
            INSERT OR IGNORE INTO connections_state (
                login, rel_type, to_login, to_bch_name, to_block_number, to_block_hash
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setInt(2, MsgSubType.CONNECTION_FRIEND);
            ps.setString(3, toLogin);
            ps.setString(4, toBchName);
            ps.setInt(5, 0);
            ps.setBytes(6, new byte[32]);
            ps.executeUpdate();
        }
    }
}
