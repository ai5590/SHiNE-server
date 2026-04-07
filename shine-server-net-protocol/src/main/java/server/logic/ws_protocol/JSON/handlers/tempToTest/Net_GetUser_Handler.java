package server.logic.ws_protocol.JSON.handlers.tempToTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.tempToTest.entyties.Net_GetUser_Request;
import server.logic.ws_protocol.JSON.handlers.tempToTest.entyties.Net_GetUser_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;

import java.sql.SQLException;
import java.util.Arrays;

public class Net_GetUser_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_GetUser_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_GetUser_Request req = (Net_GetUser_Request) baseRequest;

        if (req.getLogin() == null || req.getLogin().isBlank()) {
            // тут логичнее BAD_REQUEST, но ты просил: "нет пользователя" тоже 200.
            // Поэтому BAD_REQUEST оставляем только на реально пустой login.
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные поля: login"
            );
        }

        SolanaUsersDAO usersDAO = SolanaUsersDAO.getInstance();
        BlockchainStateDAO stateDAO = BlockchainStateDAO.getInstance();

        try {
            SolanaUserEntry u = usersDAO.getByLogin(req.getLogin());

            Net_GetUser_Response resp = new Net_GetUser_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);

            if (u == null) {
                resp.setExists(false);
                log.info("ℹ️ GetUser: not found for login={}", req.getLogin());
                return resp;
            }

            // ВАЖНО:
            // - Поиск по login был case-insensitive,
            // - а тут возвращаем login/blockchainName как в БД (с исходным регистром).
            resp.setExists(true);
            resp.setLogin(u.getLogin());
            resp.setBlockchainName(u.getBlockchainName());
            resp.setSolanaKey(u.getSolanaKey());
            resp.setBlockchainKey(u.getBlockchainKey());
            resp.setDeviceKey(u.getDeviceKey());

            // Возвращаем актуальный курсор блокчейна и, если запись состояния потеряна,
            // автоматически восстанавливаем её для существующего пользователя.
            BlockchainStateEntry st = stateDAO.getByBlockchainName(u.getBlockchainName());
            if (st == null) {
                st = new BlockchainStateEntry();
                st.setBlockchainName(u.getBlockchainName());
                st.setLogin(u.getLogin());
                st.setBlockchainKey(u.getBlockchainKey());
                st.setLastBlockNumber(-1);
                st.setLastBlockHash(new byte[32]);
                st.setFileSizeBytes(0);
                st.setSizeLimit(1_000_000L);
                st.setUpdatedAtMs(System.currentTimeMillis());
                stateDAO.upsert(st);
                log.warn("GetUser: восстановлена запись blockchain_state для login={}, blockchainName={}",
                        u.getLogin(), u.getBlockchainName());
            }

            int lastNum = st.getLastBlockNumber();
            byte[] lastHash = st.getLastBlockHash();
            if (lastHash == null || lastHash.length != 32) {
                lastHash = new byte[32];
            }
            resp.setServerLastGlobalNumber(lastNum);
            resp.setServerLastGlobalHash(toHex32(lastHash));

            log.info("✅ GetUser: found login={}, blockchainName={}", u.getLogin(), u.getBlockchainName());
            return resp;

        } catch (SQLException e) {
            log.error("❌ DB error GetUser", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка БД"
            );
        } catch (Exception e) {
            log.error("❌ Internal error GetUser", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    NetExceptionResponseFactory.detailedMessage("Внутренняя ошибка сервера при GetUser", e)
            );
        }
    }

    private static String toHex32(byte[] bytes32) {
        byte[] b = (bytes32 == null) ? new byte[32] : Arrays.copyOf(bytes32, 32);
        final char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[64];
        for (int i = 0; i < 32; i++) {
            int v = b[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
