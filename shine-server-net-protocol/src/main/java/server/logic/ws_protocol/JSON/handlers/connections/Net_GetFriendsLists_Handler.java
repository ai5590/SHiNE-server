package server.logic.ws_protocol.JSON.handlers.connections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_GetFriendsLists_Request;
import server.logic.ws_protocol.JSON.handlers.connections.entyties.Net_GetFriendsLists_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.MsgSubType;
import shine.db.SqliteDbController;
import shine.db.dao.ConnectionsStateDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * GetFriendsLists — получить 2 списка:
 *  - out_friends: кому login поставил FRIEND
 *  - in_friends: кто поставил FRIEND этому login
 *
 * ВАЖНО:
 * - login в запросе может быть любым регистром
 * - в ответе возвращаем канонический регистр (как в solana_users.login)
 *
 * ПРИМЕЧАНИЕ:
 * Таблица пользователей тут названа "solana_users". Если у тебя иначе — поменяй SQL.
 */
public class Net_GetFriendsLists_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_GetFriendsLists_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_GetFriendsLists_Request req = (Net_GetFriendsLists_Request) baseRequest;

        if (req.getLogin() == null || req.getLogin().isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные поля: login"
            );
        }

        final String loginAnyCase = req.getLogin().trim();

        try {
            SqliteDbController db = SqliteDbController.getInstance();
            ConnectionsStateDAO dao = ConnectionsStateDAO.getInstance();

            try (Connection c = db.getConnection()) {

                // 1) Канонизируем login через solana_users (NOCASE)
                String canonicalLogin = findCanonicalLogin(c, loginAnyCase);
                if (canonicalLogin == null) {
                    return NetExceptionResponseFactory.error(
                            req,
                            404,
                            "USER_NOT_FOUND",
                            "Пользователь не найден"
                    );
                }

                int relType = (int) MsgSubType.CONNECTION_FRIEND;

                // 2) Два списка (логины канонические)
                List<String> outFriends = dao.listOutgoingByRelTypeCanonical(c, canonicalLogin, relType);
                List<String> inFriends  = dao.listIncomingByRelTypeCanonical(c, canonicalLogin, relType);

                Net_GetFriendsLists_Response resp = new Net_GetFriendsLists_Response();
                resp.setOp(req.getOp());
                resp.setRequestId(req.getRequestId());
                resp.setStatus(WireCodes.Status.OK);

                resp.setLogin(canonicalLogin);
                resp.setOut_friends(outFriends);
                resp.setIn_friends(inFriends);

                return resp;
            }

        } catch (Exception e) {
            log.error("❌ Internal error GetFriendsLists", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }

    private String findCanonicalLogin(Connection c, String loginAnyCase) throws Exception {
        String sql = """
            SELECT login
            FROM solana_users
            WHERE login = ? COLLATE NOCASE
            LIMIT 1
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, loginAnyCase);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString("login");
            }
        }
    }
}