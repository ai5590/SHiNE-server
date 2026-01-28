package shine.db.entities;

import java.util.Base64;

/**
 * SolanaUserEntry — локальная запись пользователя из Solana.
 *
 * Таблица: solana_users
 *
 * Поля:
 *  - login           — PRIMARY KEY (TEXT) (case-insensitive на уровне COLLATE NOCASE)
 *  - blockchain_name — TEXT NOT NULL
 *  - solana_key      — TEXT NOT NULL
 *  - blockchain_key  — TEXT NOT NULL
 *  - device_key      — TEXT NOT NULL
 */
public class SolanaUserEntry {

    private String login;

    private String blockchainName;

    /** Ключ пользователя Solana (публичный ключ логина) */
    private String solanaKey;

    /** Ключ блокчейна (публичный ключ блокчейна) */
    private String blockchainKey;

    /** Ключ устройства (публичный ключ устройства) */
    private String deviceKey;

    public SolanaUserEntry() {}

    public SolanaUserEntry(String login,
                           String blockchainName,
                           String solanaKey,
                           String blockchainKey,
                           String deviceKey) {
        this.login = login;
        this.blockchainName = blockchainName;
        this.solanaKey = solanaKey;
        this.blockchainKey = blockchainKey;
        this.deviceKey = deviceKey;
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getBlockchainName() { return blockchainName; }
    public void setBlockchainName(String blockchainName) { this.blockchainName = blockchainName; }

    public String getSolanaKey() { return solanaKey; }
    public void setSolanaKey(String solanaKey) { this.solanaKey = solanaKey; }

    public String getBlockchainKey() { return blockchainKey; }
    public void setBlockchainKey(String blockchainKey) { this.blockchainKey = blockchainKey; }

    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }

    // оставляю этот метод как утилиту (иногда удобно), но он работает только для deviceKey:
    public byte[] getDeviceKeyByte() {
        if (deviceKey == null) return null;
        String s = deviceKey.trim();
        if (s.isEmpty()) return null;

        try {
            byte[] b = Base64.getDecoder().decode(s);
            if (b != null && b.length == 32) return b;
        } catch (IllegalArgumentException ignore) {}

        if (s.length() == 64 && s.matches("^[0-9a-fA-F]+$")) {
            byte[] out = new byte[32];
            for (int i = 0; i < 32; i++) {
                int hi = Character.digit(s.charAt(i * 2), 16);
                int lo = Character.digit(s.charAt(i * 2 + 1), 16);
                out[i] = (byte) ((hi << 4) | lo);
            }
            return out;
        }

        return null;
    }
}