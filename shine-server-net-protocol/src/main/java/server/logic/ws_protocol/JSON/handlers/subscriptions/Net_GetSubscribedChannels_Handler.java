package server.logic.ws_protocol.JSON.handlers.subscriptions;

import blockchain.BchBlockEntry;
import blockchain.body.TextBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.subscriptions.entyties.Net_GetSubscribedChannels_Request;
import server.logic.ws_protocol.JSON.handlers.subscriptions.entyties.Net_GetSubscribedChannels_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.SqliteDbController;
import shine.db.dao.SubscriptionsDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler: GetSubscribedChannels
 *
 * Логика:
 * - DAO возвращает last publication orig bytes (+ edit bytes если есть)
 * - Handler парсит FULL bytes блока:
 *      timestamp берём из ОРИГИНАЛА (publication)
 *      текст берём из EDIT (если есть) иначе из оригинала
 * - формируем превью первых 50 символов
 */
public class Net_GetSubscribedChannels_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_GetSubscribedChannels_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_GetSubscribedChannels_Request req = (Net_GetSubscribedChannels_Request) baseRequest;

        if (req.getLogin() == null || req.getLogin().isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректное поле: login"
            );
        }

        // Если хочешь жёстче:
        // if (!req.getLogin().matches("^[A-Za-z0-9_]+$")) ...

        SubscriptionsDAO dao = SubscriptionsDAO.getInstance();
        SqliteDbController db = SqliteDbController.getInstance();

        try (Connection c = db.getConnection()) {

            List<SubscriptionsDAO.ChannelRow> rows = dao.getSubscribedChannels(c, req.getLogin());
            List<Net_GetSubscribedChannels_Response.ChannelInfo> out = new ArrayList<>(rows.size());

            for (SubscriptionsDAO.ChannelRow r : rows) {
                Net_GetSubscribedChannels_Response.ChannelInfo dto =
                        new Net_GetSubscribedChannels_Response.ChannelInfo();

                dto.setChannelLogin(r.getChannelLogin());
                dto.setChannelBchName(r.getChannelBchName());
                dto.setPublicationsCount(r.getPublicationsCount());

                byte[] pubBytes = r.getLastPublicationBlockBytes();
                byte[] editBytes = r.getLastEditBlockBytes();

                if (pubBytes == null || pubBytes.length == 0) {
                    dto.setLastPublicationTimestampSec(null);
                    dto.setLastTextPreview(null);
                    out.add(dto);
                    continue;
                }

                // 1) timestamp берём из ОРИГИНАЛЬНОЙ публикации
                BchBlockEntry pubBlock = new BchBlockEntry(pubBytes);
                dto.setLastPublicationTimestampSec(pubBlock.timestamp);

                // 2) текст — из EDIT (если есть) иначе из оригинала
                byte[] actualBytes = (editBytes != null && editBytes.length > 0) ? editBytes : pubBytes;
                BchBlockEntry actualBlock = new BchBlockEntry(actualBytes);

                if (!(actualBlock.body instanceof TextBody)) {
                    // Это уже нарушение данных: last publication должен быть текстовым блоком.
                    throw new IllegalStateException("Last publication is not TextBody: type="
                            + (actualBlock.body == null ? "null" : (actualBlock.body.type() & 0xFFFF)));
                }

                String msg = ((TextBody) actualBlock.body).message;
                dto.setLastTextPreview(firstNCharsSafe(msg, 50));

                out.add(dto);
            }

            Net_GetSubscribedChannels_Response resp = new Net_GetSubscribedChannels_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);
            resp.setChannels(out);

            return resp;

        } catch (SQLException e) {
            log.error("❌ DB error GetSubscribedChannels", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка БД"
            );
        } catch (IllegalArgumentException e) {
            // сюда попадёт, например, если BchBlockEntry не смог распарсить block_byte
            log.error("❌ Bad block bytes in DB (cannot parse BchBlockEntry)", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "BAD_BLOCK_BYTES",
                    "В БД обнаружен повреждённый блок"
            );
        } catch (Exception e) {
            log.error("❌ Internal error GetSubscribedChannels", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }

    /**
     * Берём первые N "символов" безопасно для emoji/суррогатных пар:
     * режем по code points.
     */
    private static String firstNCharsSafe(String s, int n) {
        if (s == null) return null;
        if (n <= 0) return "";
        int cp = s.codePointCount(0, s.length());
        if (cp <= n) return s;
        int end = s.offsetByCodePoints(0, n);
        return s.substring(0, end);
    }
}