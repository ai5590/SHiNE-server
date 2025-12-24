package utils.blockchain;

public final class BlockchainNameUtil {

    /** Сколько символов отрезаем с конца blockchainName, чтобы получить login. */
    public static final int BLOCKCHAIN_NAME_LOGIN_SUFFIX_LEN = 3;

    private BlockchainNameUtil() {}

    /**
     * Извлечь login из blockchainName: отрезаем последние 3 символа.
     * Пример: "Dima001" -> "Dima"
     */
    public static String loginFromBlockchainName(String blockchainName) {
        if (blockchainName == null) return null;
        String s = blockchainName.trim();
        if (s.length() <= BLOCKCHAIN_NAME_LOGIN_SUFFIX_LEN) return null;
        return s.substring(0, s.length() - BLOCKCHAIN_NAME_LOGIN_SUFFIX_LEN);
    }
}