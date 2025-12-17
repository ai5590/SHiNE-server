package server.logic.ws_protocol.JSON.handlers.blockchain;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.entyties.Blockchain.Net_AddBlock_new_Request;
import server.logic.ws_protocol.JSON.entyties.Blockchain.Net_AddBlock_new_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import java.util.Base64;

public class AddBlock_new_Handler implements JsonMessageHandler {

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {

        Net_AddBlock_new_Request req = (Net_AddBlock_new_Request) baseReq;

        // 1) простая валидация запроса
        if (req.getLogin() == null || req.getLogin().isBlank())
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "EMPTY_LOGIN", "Пустой login");

        if (req.getBlockchainId() <= 0)
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_CHAIN_ID", "Некорректный blockchainId");

        if (req.getGlobalBlockNumber() < 0)
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_NUMBER", "Некорректный globalBlockNumber");

        if (req.getBlockBase64() == null || req.getBlockBase64().isBlank())
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "EMPTY_BLOCK", "Пустой blockBase64");

        byte[] blockBytes;
        try {
            blockBytes = Base64.getDecoder().decode(req.getBlockBase64());
        } catch (Exception e) {
            return NetExceptionResponseFactory.error(req, WireCodes.Status.BAD_REQUEST, "BAD_BASE64", "blockBase64 не декодируется");
        }

        // 2) основная логика — в сервис
        var r = BlockchainStateService_new.getInstance().addBlock(
                req.getLogin(),
                req.getBlockchainId(),
                req.getGlobalBlockNumber(),
                req.getPrevGlobalHashHex(),
                blockBytes
        );

        // 3) собрать ответ
        Net_AddBlock_new_Response resp = new Net_AddBlock_new_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(r.status);

        resp.setLastGlobalNumber(r.lastGlobalNumber);
        resp.setLastGlobalHashHex(r.lastGlobalHashHex);

        resp.setExpectedGlobalNumber(r.expectedGlobalNumber);
        resp.setExpectedPrevGlobalHashHex(r.expectedPrevGlobalPrevHashHex);

        return resp;
    }
}