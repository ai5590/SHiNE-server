package server.logic.ws_protocol.JSON.handlers.blockchain;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.entyties.blockchain.Net_AddBlock_Request;
import server.logic.ws_protocol.JSON.entyties.blockchain.Net_AddBlock_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.WireCodes;

import java.util.concurrent.locks.ReentrantLock;

public final class Net_AddBlock_Handler implements JsonMessageHandler {

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {

        Net_AddBlock_Request req = (Net_AddBlock_Request) baseReq;

        // todo если пришёл запрос на  добавление то надо блочить работу с этим блокчейном по         req.getBlockchainName(),
        // с помощью класса BlockchainLocks и разлочивать работу только в конце  завершения работы этого хэндлера, что бы не случилось паралельной работы двух потоков с одним и тем же блокчейном!
        ReentrantLock lock = BlockchainLocks.lockFor(req.getBlockchainName());
        lock.lock();
        try {

            var r = BlockchainStateService.getInstance().addBlockAtomically(
                    req.getBlockchainName(),
                    req.getGlobalNumber(),
                    req.getPrevGlobalHash(),
                    req.getBlockBytesB64()
            );

            Net_AddBlock_Response resp = new Net_AddBlock_Response();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());

            if (r.isOk()) {
                resp.setStatus(WireCodes.Status.OK);
                resp.setReasonCode(null);
            } else {
                resp.setStatus(r.httpStatus);
                resp.setReasonCode(r.reasonCode);
            }

            // Даже при ошибке (например bad_global_sequence) можно вернуть “что сервер считает последним”
            resp.setServerLastGlobalNumber(r.serverLastGlobalNumber);
            if (r.serverLastGlobalHash != null) {
                resp.setServerLastGlobalHash(r.serverLastGlobalHash);
            }

            return resp;

        } finally {
            lock.unlock();
        }
    }
}