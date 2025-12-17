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

/**
 * Временный хэндлер AddUser (тестовая регистрация локального пользователя).
 *
 * Ожидаемый запрос (все поля в payload):
 * {
 *   "op": "AddUser",
 *   "requestId": "...",
 *   "payload": {
 *     "login": "anya",
 *     "loginId": 100211,
 *     "bchId": 4222,
 *     "loginKey": "base64-pubkey-login",
 *     "deviceKey": "base64-pubkey-device",
 *     "bchLimit": 1000000
 *   }
 * }
 *
 * При успехе:
 *  - пользователь сохраняется в таблицу solana_users;
 *  - возвращается status=200 и пустой payload.
 */
public class Net_AddUser_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_AddUser_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) throws Exception {
        Net_AddUser_Request req = (Net_AddUser_Request) baseRequest;

        // Одна общая проверка всех ключевых полей
        if (req.getLogin() == null || req.getLogin().isBlank()
                || req.getLoginKey() == null || req.getLoginKey().isBlank()
                || req.getDeviceKey() == null || req.getDeviceKey().isBlank()
                || req.getBchLimit() == null) {

            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные или пустые поля: login, loginKey, deviceKey, bchLimit"
            );
        }

        try {
            SolanaUsersDAO dao = SolanaUsersDAO.getInstance();

            SolanaUserEntry user = new SolanaUserEntry(
                    req.getLoginId(),
                    req.getLogin(),
                    req.getBchId(),
                    req.getLoginKey(),
                    req.getDeviceKey(),
                    req.getBchLimit()
            );

            dao.insert(user);

            Net_AddUser_Response resp = new Net_AddUser_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);
            // payload станет {} через JsonInboundProcessor
            log.info("✅ Пользователь добавлен: login={}, loginId={}", req.getLogin(), req.getLoginId());
            return resp;

        } catch (SQLException e) {
            log.error("❌ Ошибка при вставке пользователя в БД", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка доступа к базе данных"
            );
        } catch (Exception e) {
            log.error("❌ Неожиданная ошибка в AddUser", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }
}
