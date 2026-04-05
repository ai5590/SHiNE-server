package shine.db.entities;

public class DirectMessageEntry {
    private String messageId;
    private String fromLogin;
    private String toLogin;
    private String text;
    private long createdAtMs;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getFromLogin() { return fromLogin; }
    public void setFromLogin(String fromLogin) { this.fromLogin = fromLogin; }

    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
}
