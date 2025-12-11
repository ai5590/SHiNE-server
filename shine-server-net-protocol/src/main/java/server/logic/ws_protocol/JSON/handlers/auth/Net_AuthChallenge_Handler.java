package server.logic.ws_protocol.JSON.handlers.auth;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.*;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_AuthChallenge_Request;
import server.logic.ws_protocol.JSON.entyties.Auth.Net_AuthChallenge_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.SolanaUser;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Шаг 1 авторизации: запрос выдачи временного nonce (authNonce).
 *
 * Клиент по логину просит сервер сгенерировать случайный authNonce,
 * который будет использован на втором шаге при подписи.
 */
public class Net_AuthChallenge_Handler implements JsonMessageHandler {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {

        Net_AuthChallenge_Request req = (Net_AuthChallenge_Request) baseReq;

        String login = req.getLogin();
        if (login == null || login.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_LOGIN",
                    "Пустой логин"
            );
        }

        // Если по этому соединению уже есть залогиненный пользователь — не даём повторную авторификацию
        if (ctx.getLogin() != null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "ALREADY_AUTHED",
                    "Попытка повторной авторификации для уже заданного login=" + ctx.getLogin()
            );
        }

        // 2) Ищем пользователя в локальной БД
        SolanaUser solanaUser = SolanaUsersDAO.getInstance().getByLogin(login);

        if (solanaUser == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "UNKNOWN_USER",
                    "Пользователь с таким логином не найден"
            );
        }

        // 3) Заполняем контекст пользователем
        ctx.setSolanaUser(solanaUser);

        // 3.1) Отмечаем, что по этому соединению начата авторификация
        ctx.setAuthenticationStatus(ConnectionContext.AUTH_STATUS_AUTH_IN_PROGRESS);

        // 4) Генерируем одноразовый authNonce = base64(32 случайных байт)
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String authNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        // Сохраняем challenge в отдельном поле authNonce
        ctx.setAuthNonce(authNonce);

        // 5) Формируем ответ
        Net_AuthChallenge_Response resp = new Net_AuthChallenge_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setAuthNonce(authNonce);

        return resp;
    }
}