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
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;

import java.sql.SQLException;

public class Net_AddUser_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_AddUser_Handler.class);

    // ====== TEST CONST (пока так) ======
    private static final int TEST_BCH_LIMIT = 1_000_000;

    private static final String ZERO64 = "0".repeat(64);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_AddUser_Request req = (Net_AddUser_Request) baseRequest;

        if (req.getLogin() == null || req.getLogin().isBlank()
                || req.getLoginKey() == null || req.getLoginKey().isBlank()
                || req.getDeviceKey() == null || req.getDeviceKey().isBlank()) {

            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные или пустые поля: login, loginKey, deviceKey"
            );
        }

        // bchLimit: если клиент не прислал — ставим тестовую константу
        Integer limit = req.getBchLimit();
        if (limit == null || limit <= 0) limit = TEST_BCH_LIMIT;

        try {
            SolanaUsersDAO users = SolanaUsersDAO.getInstance();
            BlockchainStateDAO stateDao = BlockchainStateDAO.getInstance();

            SolanaUserEntry user = new SolanaUserEntry(
                    req.getLoginId(),
                    req.getLogin(),
                    req.getBchId(),
                    req.getLoginKey(),
                    req.getDeviceKey(),
                    limit
            );

            users.insert(user);

            // Создаём стартовую запись blockchain_state
            BlockchainStateEntry s = new BlockchainStateEntry();
            s.setBlockchainId(req.getBchId());
            s.setUserLogin(req.getLogin());

            // В блокчейн-стейте храним loginKey как основной pubkey
            s.setPublicKeyBase64(req.getLoginKey());

            s.setSizeLimit(limit);
            s.setSizeBytes(0);

            // ВАЖНО: твои стартовые значения
            s.setLastGlobalNumber(-1);
            s.setLastGlobalHash(ZERO64);

            for (int i = 0; i < 8; i++) {
                s.setLastLineNumber(i, 0);
                s.setLastLineHash(i, ZERO64);
            }

            s.setUpdatedAtMs(System.currentTimeMillis());

            stateDao.upsert(s);

            Net_AddUser_Response resp = new Net_AddUser_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);

            log.info("✅ AddUser ok: login={}, loginId={}, bchId={}, limit={}",
                    req.getLogin(), req.getLoginId(), req.getBchId(), limit);

            return resp;

        } catch (SQLException e) {
            log.error("❌ DB error in AddUser", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка доступа к базе данных"
            );
        } catch (Exception e) {
            log.error("❌ Internal error in AddUser", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }
}