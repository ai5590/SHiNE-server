package server.logic.ws_protocol.JSON.handlers.tempToTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.tempToTest.entyties.Net_AddUser_Request;
import server.logic.ws_protocol.JSON.handlers.tempToTest.entyties.Net_AddUser_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;

public class Net_AddUser_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_AddUser_Handler.class);

    /** TEST ONLY */
    private static final int TEST_BCH_LIMIT = 1_000_000;

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
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

        int limit = (req.getBchLimit() == null || req.getBchLimit() <= 0)
                ? TEST_BCH_LIMIT
                : req.getBchLimit();

        try {
            byte[] blockchainKey32 = Base64.getDecoder().decode(req.getLoginKey());
            if (blockchainKey32.length != 32) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "BAD_BLOCKCHAIN_KEY",
                        "loginKey должен быть Base64(32 bytes)"
                );
            }

            SolanaUsersDAO usersDAO = SolanaUsersDAO.getInstance();
            BlockchainStateDAO stateDAO = BlockchainStateDAO.getInstance();

            SqliteDbController db = SqliteDbController.getInstance();

            try (Connection c = db.getConnection()) {
                c.setAutoCommit(false);

                // 1. Проверяем, что пользователя нет
                if (usersDAO.getByLogin(req.getLogin()) != null) {
                    return NetExceptionResponseFactory.error(
                            req,
                            409,
                            "USER_ALREADY_EXISTS",
                            "Пользователь с таким login уже существует"
                    );
                }

                // 2. Проверяем, что blockchain_state ещё нет
                if (stateDAO.getByBlockchainName(req.getBlockchainName()) != null) {
                    return NetExceptionResponseFactory.error(
                            req,
                            409,
                            "BLOCKCHAIN_ALREADY_EXISTS",
                            "blockchain_state уже существует"
                    );
                }

                // 3. Создаём пользователя
                SolanaUserEntry user = new SolanaUserEntry(
                        req.getLogin(),
                        req.getDeviceKey(),
                        req.getDeviceKey()
                );

                usersDAO.insert(c, user);

                // 4. Создаём INITIAL blockchain_state
                BlockchainStateEntry st = new BlockchainStateEntry();
                st.setBlockchainName(req.getBlockchainName());
                st.setLogin(req.getLogin());
                st.setBlockchainKey(req.getLoginKey()); // Base64(32)
                st.setLastGlobalNumber(-1);
                st.setLastGlobalHash("");
                st.setFileSizeBytes(0);
                st.setSizeLimit(limit);
                st.setUpdatedAtMs(System.currentTimeMillis());

                stateDAO.upsert(c, st);

                c.commit();
            }

            Net_AddUser_Response resp = new Net_AddUser_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);

            log.info("✅ AddUser ok: login={}, blockchainName={}, limit={}",
                    req.getLogin(), req.getBlockchainName(), limit);

            return resp;

        } catch (IllegalArgumentException e) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_KEY_FORMAT",
                    e.getMessage()
            );
        } catch (SQLException e) {
            log.error("❌ DB error AddUser", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка БД"
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