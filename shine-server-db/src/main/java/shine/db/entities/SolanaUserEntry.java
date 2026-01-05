package shine.db.entities;

import java.util.Base64;

/**
 * SolanaUserEntry — локальная запись пользователя из Solana.
 *
 * Таблица: solana_users
 *
 * Поля:
 *  - login      — PRIMARY KEY (TEXT)
 *  - device_key — TEXT NOT NULL
 *  - solana_key — TEXT NULLABLE
 */
public class SolanaUserEntry {

    private String login;
    private String deviceKey;
    private String solanaKey;

    public SolanaUserEntry() {}

    public SolanaUserEntry(String login, String deviceKey) {
        this.login = login;
        this.deviceKey = deviceKey;
    }

    public SolanaUserEntry(String login, String deviceKey, String solanaKey) {
        this.login = login;
        this.deviceKey = deviceKey;
        this.solanaKey = solanaKey;
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }

    public String getSolanaKey() { return solanaKey; }
    public void setSolanaKey(String solanaKey) { this.solanaKey = solanaKey; }

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