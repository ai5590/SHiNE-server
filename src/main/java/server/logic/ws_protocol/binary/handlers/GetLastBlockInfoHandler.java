//package server.logic.ws_protocol.binary.handlers;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import server.logic.ws_protocol.WireCodes;
//import utils.blockchain.BchInfoEntry;
//import utils.blockchain.BchInfoManager;
//
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.util.Arrays;
//
///**
// * Возврат информации о последнем блоке цепочки (GET_LAST_BLOCK_INFO).
// */
//public class GetLastBlockInfoHandler implements MessageHandler {
//    private static final Logger log = LoggerFactory.getLogger(GetLastBlockInfoHandler.class);
//
//    @Override
//    public byte[] handle(byte[] msg) {
//        try {
//            if (msg.length < 12)
//                return intTo4Bytes(WireCodes.Status.BAD_REQUEST);
//
//            long blockchainId = ByteBuffer.wrap(msg, 4, 8)
//                    .order(ByteOrder.BIG_ENDIAN)
//                    .getLong();
//
//            BchInfoManager mgr = BchInfoManager.getInstance();
//            BchInfoEntry entry = mgr.getBchInfo(blockchainId);
//            if (entry == null)
//                return intTo4Bytes(WireCodes.Status.CHAIN_NOT_FOUND);
//
//            int lastNum = entry.lastBlockNumber;
//            byte[] hash = hexToBytes(entry.lastBlockHash);
//
//            ByteBuffer out = ByteBuffer.allocate(4 + 4 + 32).order(ByteOrder.BIG_ENDIAN);
//            out.putInt(WireCodes.Status.OK);
//            out.putInt(lastNum);
//            out.put(hash);
//            return out.array();
//
//        } catch (Exception e) {
//            log.error("GET_LAST_BLOCK_INFO: ошибка", e);
//            return intTo4Bytes(WireCodes.Status.INTERNAL_ERROR);
//        }
//    }
//
//    private static byte[] intTo4Bytes(int code) {
//        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(code).array();
//    }
//
//    private static byte[] hexToBytes(String hex) {
//        if (hex == null || hex.isEmpty()) return new byte[32];
//        int len = hex.length();
//        byte[] out = new byte[len / 2];
//        for (int i = 0; i < len; i += 2)
//            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
//        if (out.length < 32) { // добиваем нулями
//            byte[] full = new byte[32];
//            System.arraycopy(out, 0, full, 32 - out.length, out.length);
//            return full;
//        }
//        return Arrays.copyOf(out, 32);
//    }
//}
