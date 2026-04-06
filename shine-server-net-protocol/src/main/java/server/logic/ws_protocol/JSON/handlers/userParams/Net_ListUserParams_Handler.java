package server.logic.ws_protocol.JSON.handlers.userParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_ListUserParams_Request;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_ListUserParams_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.SqliteDbController;
import shine.db.dao.SolanaUsersDAO;
import shine.db.dao.UserParamsDAO;
import shine.db.entities.SolanaUserEntry;
import shine.db.entities.UserParamEntry;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * ListUserParams — получить все параметры пользователя.
 *
 * ПРО ДОСТУП (на будущее):
 * ---------------------------------------------------------------------------------
 * Сейчас (MVP) запрос не ограничивает просмотр параметров.
 * В будущем, вероятно, потребуется проверка сессии/прав: кто может читать параметры.
 * Для MVP эти проверки не нужны.
 * ---------------------------------------------------------------------------------
 */
public class Net_ListUserParams_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_ListUserParams_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_ListUserParams_Request req = (Net_ListUserParams_Request) baseRequest;

        if (req.getLogin() == null || req.getLogin().isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные поля: login"
            );
        }

        String login = req.getLogin().trim();

        try {
            SqliteDbController db = SqliteDbController.getInstance();
            UserParamsDAO dao = UserParamsDAO.getInstance();

            List<UserParamEntry> entries;
            try (Connection c = db.getConnection()) {
                entries = dao.getByLogin(c, login);
            }

            Net_ListUserParams_Response resp = new Net_ListUserParams_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);

            SolanaUserEntry user = SolanaUsersDAO.getInstance().getByLogin(login);
            resp.setLogin(user != null && user.getLogin() != null ? user.getLogin() : login);

            List<Net_ListUserParams_Response.Item> items = new ArrayList<>();
            for (UserParamEntry e : entries) {
                Net_ListUserParams_Response.Item it = new Net_ListUserParams_Response.Item();
                it.setLogin(e.getLogin());
                it.setParam(e.getParam());
                it.setTime_ms(e.getTimeMs());
                it.setValue(e.getValue());
                it.setDevice_key(e.getDeviceKey());
                it.setSignature(e.getSignature());
                items.add(it);
            }
            resp.setParams(items);

            return resp;

        } catch (Exception e) {
            log.error("❌ Internal error ListUserParams", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    NetExceptionResponseFactory.detailedMessage("Внутренняя ошибка сервера при ListUserParams", e)
            );
        }
    }
}
