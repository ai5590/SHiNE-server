package server.logic.ws_protocol.JSON.handlers.auth;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_AuthChallenge_Request;
import server.logic.ws_protocol.JSON.handlers.auth.entyties.Net_AuthChallenge_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.SolanaUserEntry;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * AuthChallenge (v2) — шаг 1 создания новой сессии.
 *
 * Логика авторизации (v2):
 * - Создание новой сессии возможно ТОЛЬКО через deviceKey пользователя.
 * - Этот handler выдаёт одноразовый authNonce, который клиент использует во втором шаге:
 *   CreateAuthSession(..., signature(deviceKey, AUTH_CREATE_SESSION:...))
 *
 * Что делает:
 * 1) Проверяет login.
 * 2) Находит пользователя (solana_users).
 * 3) Пишет solanaUser в ctx, ставит AUTH_STATUS_AUTH_IN_PROGRESS.
 * 4) Генерирует authNonce (base64url(32)) и сохраняет в ctx.authNonce.
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

        SolanaUserEntry solanaUserEntry = SolanaUsersDAO.getInstance().getByLogin(login);
        if (solanaUserEntry == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "UNKNOWN_USER",
                    "Пользователь с таким логином не найден"
            );
        }

        ctx.setSolanaUser(solanaUserEntry);
        ctx.setAuthenticationStatus(ConnectionContext.AUTH_STATUS_AUTH_IN_PROGRESS);

        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String authNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        ctx.setAuthNonce(authNonce);

        Net_AuthChallenge_Response resp = new Net_AuthChallenge_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setAuthNonce(authNonce);

        return resp;
    }
}