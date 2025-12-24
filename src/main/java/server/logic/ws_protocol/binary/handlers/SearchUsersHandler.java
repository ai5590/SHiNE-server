//package server.logic.ws_protocol.binary.handlers;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import server.logic.ws_protocol.WireCodes;
//import utils.search.UserSearchService;
//
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//
///**
// * Поиск пользователей по логину (SEARCH_USERS).
// */
//public class SearchUsersHandler implements MessageHandler {
//    private static final Logger log = LoggerFactory.getLogger(SearchUsersHandler.class);
//
//    @Override
//    public byte[] handle(byte[] msg) {
//        try {
//            if (msg.length < 8)
//                return intTo4Bytes(WireCodes.Status.BAD_REQUEST);
//
//            int N = ByteBuffer.wrap(msg, 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();
//            if (N < 0 || msg.length < 8 + N)
//                return intTo4Bytes(WireCodes.Status.BAD_REQUEST);
//
//            String query = new String(msg, 8, N, StandardCharsets.UTF_8);
//            List<UserSearchService.Pair> found = UserSearchService.getInstance().searchFirst5(query);
//            return pack(found);
//
//        } catch (Exception e) {
//            log.error("SEARCH_USERS: ошибка", e);
//            return intTo4Bytes(WireCodes.Status.INTERNAL_ERROR);
//        }
//    }
//
//    private static byte[] pack(List<UserSearchService.Pair> pairs) {
//        if (pairs == null) pairs = List.of();
//        int total = 8;
//        var chunks = new java.util.ArrayList<byte[]>();
//        for (var p : pairs) {
//            byte[] packed = UserSearchService.packPair(p);
//            chunks.add(packed);
//            total += packed.length;
//        }
//
//        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);
//        out.putInt(WireCodes.Status.OK);
//        out.putInt(pairs.size());
//        for (var c : chunks) out.put(c);
//        return out.array();
//    }
//
//    private static byte[] intTo4Bytes(int code) {
//        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(code).array();
//    }
//}
