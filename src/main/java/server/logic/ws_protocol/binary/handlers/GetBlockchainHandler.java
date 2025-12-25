//package server.logic.ws_protocol.binary.handlers;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import server.logic.ws_protocol.WireCodes;
//import utils.files.FileStoreUtil;
//
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//
///**
// * Возврат полного содержимого блокчейна (GET_BLOCKCHAIN).
// */
//public class GetBlockchainHandler implements MessageHandler {
//    private static final Logger log = LoggerFactory.getLogger(GetBlockchainHandler.class);
//
//    @Override
//    public byte[] handle(byte[] msg) {
//        try {
//            if (msg.length < 12)
//                return intTo4Bytes(WireCodes.Status.BAD_REQUEST);
//
//            long id = ByteBuffer.wrap(msg, 4, 8)
//                    .order(ByteOrder.BIG_ENDIAN)
//                    .getLong();
//
//            FileStoreUtil fs = FileStoreUtil.getInstance();
//            byte[] data = fs.readAllDataFromBlockchain(id);
//
//            return packOk(data);
//
//        } catch (IllegalStateException e) {
//            log.warn("GET_BLOCKCHAIN: файл не найден ({})", e.getMessage());
//            return intTo4Bytes(WireCodes.Status.CHAIN_NOT_FOUND);
//        } catch (Exception e) {
//            log.error("GET_BLOCKCHAIN: ошибка", e);
//            return intTo4Bytes(WireCodes.Status.INTERNAL_ERROR);
//        }
//    }
//
//    private static byte[] packOk(byte[] data) {
//        if (data == null) data = new byte[0];
//        ByteBuffer out = ByteBuffer.allocate(8 + data.length).order(ByteOrder.BIG_ENDIAN);
//        out.putInt(WireCodes.Status.OK);
//        out.putInt(data.length);
//        out.put(data);
//        return out.array();
//    }
//
//    private static byte[] intTo4Bytes(int code) {
//        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(code).array();
//    }
//}
