package server.logic.ws_protocol.JSON.handlers.userParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_UpsertUserParam_Request;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_UpsertUserParam_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.SqliteDbController;
import shine.db.dao.SolanaUsersDAO;
import shine.db.dao.UserParamsDAO;
import shine.db.entities.SolanaUserEntry;
import shine.db.entities.UserParamEntry;
import utils.config.ShineSignatureConstants;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;

/**
 * Net_UpsertUserParam_Handler
 *
 * Делает (MVP, без "сессий"):
 *  1) Проверка входных полей.
 *  2) Проверка подписи Ed25519 по device_key.
 *  3) Проверка, что пользователь существует и что device_key принадлежит этому login.
 *  4) Атомарная запись в БД "только если time_ms новее" (UPSERT + WHERE).
 *
 * ВАЖНО:
 *  - НИКАКИХ ручных транзакций / BEGIN здесь нет.
 *  - autoCommit=true, каждый statement завершённый сам по себе.
 *  - Гонки не страшны: если за время проверок кто-то записал более новый time_ms,
 *    наш финальный UPSERT просто вернёт 0 обновлённых строк.
 */
public class Net_UpsertUserParam_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_UpsertUserParam_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_UpsertUserParam_Request req = (Net_UpsertUserParam_Request) baseRequest;

        if (req.getLogin() == null || req.getLogin().isBlank()
                || req.getParam() == null || req.getParam().isBlank()
                || req.getTime_ms() == null || req.getTime_ms() <= 0
                || req.getValue() == null
                || req.getDevice_key() == null || req.getDevice_key().isBlank()
                || req.getSignature() == null || req.getSignature().isBlank()) {

            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные поля: login/param/time_ms/value/device_key/signature"
            );
        }

        final String login = req.getLogin().trim();
        final String param = req.getParam().trim();
        final long timeMs = req.getTime_ms();
        final String value = req.getValue();
        final String deviceKeyB64 = req.getDevice_key().trim();
        final String signatureB64 = req.getSignature().trim();

        try {
            // ---------------- Base64 decode ----------------
            byte[] pubKey32;
            byte[] sig64;
            try {
                pubKey32 = Base64.getDecoder().decode(deviceKeyB64);
                sig64 = Base64.getDecoder().decode(signatureB64);
            } catch (IllegalArgumentException e) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "BAD_BASE64",
                        "device_key/signature должны быть Base64"
                );
            }

            if (pubKey32.length != 32) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "BAD_DEVICE_KEY",
                        "device_key должен быть Base64(32 bytes)"
                );
            }
            if (sig64.length != 64) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "BAD_SIGNATURE",
                        "signature должна быть Base64(64 bytes)"
                );
            }

            // ---------------- Signature verify ----------------
            String signText = ShineSignatureConstants.USER_PARAMETER_PREFIX
                    + login
                    + param
                    + timeMs
                    + value;

            byte[] signBytes = signText.getBytes(StandardCharsets.UTF_8);

            boolean sigOk = Ed25519Util.verify(signBytes, sig64, pubKey32);
            if (!sigOk) {
                return NetExceptionResponseFactory.error(
                        req,
                        403,
                        "SIGNATURE_INVALID",
                        "Подпись не прошла проверку"
                );
            }

            // ---------------- DB checks + upsert ----------------
            SqliteDbController db = SqliteDbController.getInstance();
            SolanaUsersDAO usersDAO = SolanaUsersDAO.getInstance();
            UserParamsDAO paramsDAO = UserParamsDAO.getInstance();

            try (Connection c = db.getConnection()) {
                // 1) user exists
                SolanaUserEntry user = usersDAO.getByLogin(c, login);
                if (user == null) {
                    return NetExceptionResponseFactory.error(
                            req,
                            404,
                            "USER_NOT_FOUND",
                            "Пользователь не найден"
                    );
                }

                // 2) device key must match the user's stored deviceKey
                String userDeviceKey = user.getDeviceKey();
                if (userDeviceKey == null || userDeviceKey.isBlank()) {
                    return NetExceptionResponseFactory.error(
                            req,
                            WireCodes.Status.SERVER_DATA_ERROR,
                            "USER_DEVICE_KEY_EMPTY",
                            "У пользователя не задан deviceKey в БД"
                    );
                }

                if (!userDeviceKey.trim().equals(deviceKeyB64)) {
                    return NetExceptionResponseFactory.error(
                            req,
                            403,
                            "DEVICE_KEY_MISMATCH",
                            "device_key не соответствует пользователю"
                    );
                }

                // 3) atomic upsert-if-newer
                UserParamEntry e = new UserParamEntry(
                        login,
                        param,
                        timeMs,
                        value,
                        deviceKeyB64,
                        signatureB64
                );

                int changed = paramsDAO.upsertIfNewer(c, e);

                Net_UpsertUserParam_Response resp = new Net_UpsertUserParam_Response();
                resp.setOp(req.getOp());
                resp.setRequestId(req.getRequestId());
                resp.setStatus(WireCodes.Status.OK);

                if (changed == 1) {
                    log.info("✅ UpsertUserParam applied: login={}, param={}, time_ms={}", login, param, timeMs);
                } else {
                    // 0 строк — значит в БД уже есть time_ms >= incoming
                    log.info("ℹ️ UpsertUserParam ignored (not newer): login={}, param={}, time_ms={}", login, param, timeMs);
                }

                return resp;
            }

        } catch (SQLException e) {
            log.error("❌ DB error UpsertUserParam", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка БД"
            );
        } catch (Exception e) {
            log.error("❌ Internal error UpsertUserParam", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }
}