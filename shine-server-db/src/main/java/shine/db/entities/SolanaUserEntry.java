package shine.db.entities;

/**
 * Локальная копия пользователя из Solana.
 *
 * Храним:
 *  - login / loginId;
 *  - bchId — id персонального блокчейна;
 *  - loginKey  — публичный ключ для логина / авторизации;
 *  - deviceKey — публичный ключ устройства (второй ключ);
 *  - bchLimit  — лимит по количеству блоков / размеру цепочки (может быть null).
 */
public class SolanaUserEntry {

    private long loginId;
    private String login;
    private long bchId;
    private String loginKey;   // раньше pubkey0
    private String deviceKey;  // раньше pubkey1
    private Integer bchLimit;  // может быть null

    public SolanaUserEntry() {
    }

    public SolanaUserEntry(long loginId,
                           String login,
                           long bchId,
                           String loginKey,
                           String deviceKey,
                           Integer bchLimit) {
        this.loginId = loginId;
        this.login = login;
        this.bchId = bchId;
        this.loginKey = loginKey;
        this.deviceKey = deviceKey;
        this.bchLimit = bchLimit;
    }

    public long getLoginId() {
        return loginId;
    }

    public void setLoginId(long loginId) {
        this.loginId = loginId;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public long getBchId() {
        return bchId;
    }

    public void setBchId(long bchId) {
        this.bchId = bchId;
    }

    /** Публичный ключ логина (основной ключ пользователя). */
    public String getLoginKey() {
        return loginKey;
    }

    public void setLoginKey(String loginKey) {
        this.loginKey = loginKey;
    }

    /** Публичный ключ устройства (device key). */
    public String getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey) {
        this.deviceKey = deviceKey;
    }

    public Integer getBchLimit() {
        return bchLimit;
    }

    public void setBchLimit(Integer bchLimit) {
        this.bchLimit = bchLimit;
    }
}
