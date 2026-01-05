package shine.db.entities;

/**
 * UserParamEntry — сохранённый параметр пользователя.
 *
 * Таблица: users_params
 *  - login      TEXT    NOT NULL
 *  - param      TEXT    NOT NULL
 *  - time_ms    INTEGER NOT NULL
 *  - value      TEXT    NOT NULL
 *  - device_key TEXT    NULL
 *  - signature  TEXT    NULL
 */
public class UserParamEntry {

    private String login;
    private String param;
    private long timeMs;
    private String value;

    private String deviceKey;
    private String signature;

    public UserParamEntry() {}

    public UserParamEntry(String login, String param, long timeMs, String value, String deviceKey, String signature) {
        this.login = login;
        this.param = param;
        this.timeMs = timeMs;
        this.value = value;
        this.deviceKey = deviceKey;
        this.signature = signature;
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getParam() { return param; }
    public void setParam(String param) { this.param = param; }

    public long getTimeMs() { return timeMs; }
    public void setTimeMs(long timeMs) { this.timeMs = timeMs; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}