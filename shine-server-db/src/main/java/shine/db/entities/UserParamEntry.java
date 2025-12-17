package shine.db.entities;

public class UserParamEntry {

    private long loginId;
    private String param;
    private long bchChannelId;   // новый канал, 8 байт, может быть 0
    private String value;
    private long timeMs;         // время в мс
    private short pubkeyNum;
    private String signature;

    public UserParamEntry() {
    }

    public UserParamEntry(long loginId,
                          String param,
                          long bchChannelId,
                          String value,
                          long timeMs,
                          short pubkeyNum,
                          String signature) {
        this.loginId = loginId;
        this.param = param;
        this.bchChannelId = bchChannelId;
        this.value = value;
        this.timeMs = timeMs;
        this.pubkeyNum = pubkeyNum;
        this.signature = signature;
    }

    public long getLoginId() {
        return loginId;
    }

    public void setLoginId(long loginId) {
        this.loginId = loginId;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public long getBchChannelId() {
        return bchChannelId;
    }

    public void setBchChannelId(long bchChannelId) {
        this.bchChannelId = bchChannelId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(long timeMs) {
        this.timeMs = timeMs;
    }

    public short getPubkeyNum() {
        return pubkeyNum;
    }

    public void setPubkeyNum(short pubkeyNum) {
        this.pubkeyNum = pubkeyNum;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
