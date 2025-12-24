package server.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.binary.handlers.*;
import server.logic.ws_protocol.WireCodes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * Обработчик входящих сообщение на сервер.
 * По коду сообщения (первые 4 байта сообщения) находи нужный хэндлер и передаёт в него сообщение
 * Получает и возвращает ответ от хэндлера
 */
public final class InboundMessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(InboundMessageProcessor.class);

    private static final Map<Integer, MessageHandler> HANDLERS = Map.of(
            WireCodes.Op.PING,          new PingHandler(),
//            WireCodes.Op.ADD_BLOCK,     new AddBlockHandler(),
            WireCodes.Op.GET_BLOCKCHAIN,new GetBlockchainHandler()
//            WireCodes.Op.SEARCH_USERS,  new SearchUsersHandler(),
//            WireCodes.Op.GET_LAST_BLOCK_INFO,new GetLastBlockInfoHandler()

    );

    private InboundMessageProcessor() {}

    public static byte[] process(byte[] msg) {
        if (msg == null || msg.length < 4)
            return intTo4Bytes(WireCodes.Status.BAD_REQUEST);

        int op = first4ToInt(msg);
        MessageHandler h = HANDLERS.get(op);
        if (h == null) {
            log.warn("Неизвестная операция: {}", op);
            return intTo4Bytes(WireCodes.Status.BAD_REQUEST);
        }

        try {
            return h.handle(msg);
        } catch (Exception e) {
            log.error("Ошибка при обработке операции {}", op, e);
            return intTo4Bytes(WireCodes.Status.INTERNAL_ERROR);
        }
    }

    private static int first4ToInt(byte[] msg) {
        return ByteBuffer.wrap(msg, 0, 4)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();
    }

    public static byte[] intTo4Bytes(int code) {
        return ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(code)
                .array();
    }



}

