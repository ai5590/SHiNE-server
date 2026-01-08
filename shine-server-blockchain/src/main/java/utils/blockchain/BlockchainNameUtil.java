package utils.blockchain;

import java.util.Objects;

public final class BlockchainNameUtil {

    /**
     * Теперь новое правило:
     * blockchainName = login + "-"+ 3 цифры
     * Пример: "Dima-001" -> "Dima"
     *
     * Сколько символов отрезаем с конца blockchainName, чтобы получить login: "-001" = 4
     */
    public static final int BLOCKCHAIN_NAME_LOGIN_SUFFIX_LEN = 4;

    private BlockchainNameUtil() {}

    /**
     * Извлечь login из blockchainName: отрезаем последние 4 символа ("-NNN").
     * Пример: "Dima-001" -> "Dima"
     */
    public static String loginFromBlockchainName(String blockchainName) {
        if (blockchainName == null) return null;

        String s = blockchainName.trim();
        if (!hasDashAnd3DigitsSuffix(s)) return null;

        return s.substring(0, s.length() - BLOCKCHAIN_NAME_LOGIN_SUFFIX_LEN);
    }

    /**
     * Проверка правила:
     *  - blockchainName должен оканчиваться на "-"+3 цифры
     *  - blockchainName без суффикса "-NNN" должен равняться login
     *
     * ВАЖНО:
     *  - сравнение строгое (case-sensitive)
     *  - null/blank считаем невалидным
     */
    public static boolean isBlockchainNameMatchesLogin(String blockchainName, String login) {
        if (blockchainName == null || login == null) return false;

        String bn = blockchainName.trim();
        String lg = login.trim();

        if (bn.isEmpty() || lg.isEmpty()) return false;
        if (!hasDashAnd3DigitsSuffix(bn)) return false;

        String extracted = bn.substring(0, bn.length() - BLOCKCHAIN_NAME_LOGIN_SUFFIX_LEN);
        return Objects.equals(extracted, lg);
    }

    private static boolean hasDashAnd3DigitsSuffix(String s) {
        if (s == null) return false;
        int len = s.length();
        if (len <= BLOCKCHAIN_NAME_LOGIN_SUFFIX_LEN) return false;

        int dashPos = len - 4;
        if (s.charAt(dashPos) != '-') return false;

        char c1 = s.charAt(len - 3);
        char c2 = s.charAt(len - 2);
        char c3 = s.charAt(len - 1);

        return isDigit(c1) && isDigit(c2) && isDigit(c3);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}