package shine.db.entities;

public class SignedDirectMessageHistoryEntry {
    private String messageId;
    private String fromLogin;
    private String toLogin;
    private int targetMode;
    private String targetSessionId;
    private int messageType;
    private long timeMs;
    private long nonce;
    private byte[] rawPacket;
    private long createdAtMs;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getFromLogin() { return fromLogin; }
    public void setFromLogin(String fromLogin) { this.fromLogin = fromLogin; }
    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }
    public int getTargetMode() { return targetMode; }
    public void setTargetMode(int targetMode) { this.targetMode = targetMode; }
    public String getTargetSessionId() { return targetSessionId; }
    public void setTargetSessionId(String targetSessionId) { this.targetSessionId = targetSessionId; }
    public int getMessageType() { return messageType; }
    public void setMessageType(int messageType) { this.messageType = messageType; }
    public long getTimeMs() { return timeMs; }
    public void setTimeMs(long timeMs) { this.timeMs = timeMs; }
    public long getNonce() { return nonce; }
    public void setNonce(long nonce) { this.nonce = nonce; }
    public byte[] getRawPacket() { return rawPacket; }
    public void setRawPacket(byte[] rawPacket) { this.rawPacket = rawPacket; }
    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
}
