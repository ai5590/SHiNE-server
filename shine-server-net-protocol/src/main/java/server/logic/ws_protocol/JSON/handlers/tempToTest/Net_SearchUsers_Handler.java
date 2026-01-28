package server.logic.ws_protocol.JSON.handlers.tempToTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.tempToTest.entyties.Net_SearchUsers_Request;
import server.logic.ws_protocol.JSON.handlers.tempToTest.entyties.Net_SearchUsers_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.SolanaUserEntry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Net_SearchUsers_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_SearchUsers_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_SearchUsers_Request req = (Net_SearchUsers_Request) baseRequest;

        if (req.getPrefix() == null || req.getPrefix().isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные поля: prefix"
            );
        }

        String prefix = req.getPrefix().trim();

        try {
            SolanaUsersDAO dao = SolanaUsersDAO.getInstance();
            List<SolanaUserEntry> users = dao.searchByLoginPrefix(prefix); // case-insensitive + LIMIT 5

            List<String> logins = new ArrayList<>();
            for (SolanaUserEntry u : users) {
                if (u != null && u.getLogin() != null) {
                    logins.add(u.getLogin()); // регистр как в БД
                }
            }

            Net_SearchUsers_Response resp = new Net_SearchUsers_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);
            resp.setLogins(logins);

            log.info("✅ SearchUsers ok: prefix='{}' -> {}", prefix, logins.size());
            return resp;

        } catch (SQLException e) {
            log.error("❌ DB error SearchUsers", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка БД"
            );
        } catch (Exception e) {
            log.error("❌ Internal error SearchUsers", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }
}