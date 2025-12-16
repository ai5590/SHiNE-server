package utils.search;


import utils.blockchain.BchInfoManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UserSearchService — поиск первых 5 пользователей по подстроке логина (без учёта регистра).
 */
public final class UserSearchService {

    private static final UserSearchService INSTANCE = new UserSearchService();
    private UserSearchService() {}
    public static UserSearchService getInstance() { return INSTANCE; }

    /** Результат одной пары: id + исходный login (с родным регистром). */
    public static final class Pair {
        public final long blockchainId;
        public final String userLogin;
        public Pair(long blockchainId, String userLogin) {
            this.blockchainId = blockchainId;
            this.userLogin = userLogin;
        }
    }

    /**
     * Найти первые до 5 логинов, содержащих подстроку (case-insensitive).
     */
    public List<Pair> searchFirst5(String query) {
        String q = (query == null ? "" : query).toLowerCase(Locale.ROOT).trim();
        List<Pair> out = new ArrayList<>(5);
        if (q.isEmpty()) return out;

        // берём снапшот id→login
        Map<Long, String> all = BchInfoManager.getInstance().getAllLoginsSnapshot();

        for (var e : all.entrySet()) {
            if (out.size() >= 5) break;
            String login = e.getValue() == null ? "" : e.getValue();
            if (login.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Pair(e.getKey(), login));
            }
        }
        return out;
    }

    // Упаковка пары в байтовый формат ответа: [8] id + [1] L + [L] login UTF-8 (L<=255)
    public static byte[] packPair(Pair p) {
        byte[] loginUtf8 = (p.userLogin == null ? "" : p.userLogin).getBytes(StandardCharsets.UTF_8);
        int L = Math.min(loginUtf8.length, 255);
        byte[] b = new byte[8 + 1 + L];
        // beLong
        b[0]=(byte)((p.blockchainId>>>56)&0xFF);
        b[1]=(byte)((p.blockchainId>>>48)&0xFF);
        b[2]=(byte)((p.blockchainId>>>40)&0xFF);
        b[3]=(byte)((p.blockchainId>>>32)&0xFF);
        b[4]=(byte)((p.blockchainId>>>24)&0xFF);
        b[5]=(byte)((p.blockchainId>>>16)&0xFF);
        b[6]=(byte)((p.blockchainId>>>8 )&0xFF);
        b[7]=(byte)((p.blockchainId     )&0xFF);
        b[8]=(byte)L;
        System.arraycopy(loginUtf8, 0, b, 9, L);
        return b;
    }
}
