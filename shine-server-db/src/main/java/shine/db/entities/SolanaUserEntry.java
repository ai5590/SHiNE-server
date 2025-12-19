package shine.db.entities;

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
    private String bchName;    // TEXT NOT NULL
    private String loginKey;   // TEXT
    private String deviceKey;  // TEXT
    private Integer bchLimit;  // INTEGER nullable

    public SolanaUserEntry() {
    }

    public SolanaUserEntry(String login,
                           String bchName,
                           String loginKey,
                           String deviceKey,
                           Integer bchLimit) {
        this.login = login;
        this.bchName = bchName;
        this.loginKey = loginKey;
        this.deviceKey = deviceKey;
        this.bchLimit = bchLimit;
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getBchName() { return bchName; }
    public void setBchName(String bchName) { this.bchName = bchName; }

    /** Публичный ключ логина (основной ключ пользователя). */
    public String getLoginKey() { return loginKey; }
    public void setLoginKey(String loginKey) { this.loginKey = loginKey; }

    /** Публичный ключ устройства (device key). */
    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }

    public Integer getBchLimit() { return bchLimit; }
    public void setBchLimit(Integer bchLimit) { this.bchLimit = bchLimit; }
}