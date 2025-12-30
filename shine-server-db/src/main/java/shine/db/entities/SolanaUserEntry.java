package shine.db.entities;

import java.util.Base64;

/**
 * Локальная копия пользователя из Solana.
 *
 * Теперь:
 *  - login     — PRIMARY KEY (TEXT)
 *  - bchName   — имя/идентификатор персонального блокчейна (TEXT)
 *  - loginKey  — публичный ключ логина
 *  - deviceKey — публичный ключ устройства
 *  - bchLimit  — лимит (может быть null)
 */
public class SolanaUserEntry {

    private String login;      // TEXT PK
    private String deviceKey;  // TEXT NOT NULL (Base64(32 bytes))

    public SolanaUserEntry() {}

    public SolanaUserEntry(String login, String deviceKey) {
        this.login = login;
        this.deviceKey = deviceKey;
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    /** Публичный ключ устройства (device key). */
    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }

    /**
     * Device key в байтах (32 байта) или null, если ключ битый/пустой.
     *
     * Поддержка форматов:
     *  - Base64 (предпочтительно)
     *  - HEX (ровно 64 hex-символа, без пробелов)
     */
    public byte[] getDeviceKeyByte() {
        if (deviceKey == null) return null;
        String s = deviceKey.trim();
        if (s.isEmpty()) return null;

        // 1) пробуем Base64
        try {
            byte[] b = Base64.getDecoder().decode(s);
            if (b != null && b.length == 32) return b;
        } catch (IllegalArgumentException ignore) {}

        // 2) пробуем HEX (64 символа)
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