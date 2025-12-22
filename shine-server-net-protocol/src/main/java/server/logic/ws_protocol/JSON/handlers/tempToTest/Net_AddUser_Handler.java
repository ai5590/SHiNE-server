package server.logic.ws_protocol.JSON.handlers.tempToTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.entyties.tempToTest.Net_AddUser_Request;
import server.logic.ws_protocol.JSON.entyties.tempToTest.Net_AddUser_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.SolanaUserEntry;

import java.sql.SQLException;

public class Net_AddUser_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_AddUser_Handler.class);

    /** TEST ONLY: лимит блокчейна по умолчанию. Потом заменишь на норм логику. */
    private static final int TEST_BCH_LIMIT = 1_000_000;

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_AddUser_Request req = (Net_AddUser_Request) baseRequest;

        if (req.getLogin() == null || req.getLogin().isBlank()
                || req.getBlockchainName() == null || req.getBlockchainName().isBlank()
                || req.getLoginKey() == null || req.getLoginKey().isBlank()
                || req.getDeviceKey() == null || req.getDeviceKey().isBlank()) {

            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные поля: login/blockchainName/loginKey/deviceKey"
            );
        }

        Integer limit = req.getBchLimit();
        if (limit == null || limit <= 0) limit = TEST_BCH_LIMIT;

        try {
            SolanaUsersDAO dao = SolanaUsersDAO.getInstance();

            SolanaUserEntry user = new SolanaUserEntry(
                    req.getLogin(),
                    req.getBlockchainName(),
                    req.getLoginKey(),
                    req.getDeviceKey(),
                    limit
            );

            dao.insert(user);

            Net_AddUser_Response resp = new Net_AddUser_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);

            log.info("✅ AddUser ok: login={}, blockchainName={}, limit={}",
                    req.getLogin(), req.getBlockchainName(), limit);

            return resp;

        } catch (SQLException e) {
            log.error("❌ DB error AddUser", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка доступа к базе данных"
            );
        } catch (Exception e) {
            log.error("❌ Internal error AddUser", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }
}