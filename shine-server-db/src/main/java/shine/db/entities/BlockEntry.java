package shine.db.entities;

/**
 * Запись блока (таблица blocks).
 *
 * Теперь:
 *  - login       TEXT NOT NULL
 *  - bchName     TEXT NOT NULL (идёт сразу после login)
 *  - to_login    TEXT nullable
 *  - toBchName   TEXT NOT NULL (идёт сразу после to_login)
 *
 * PRIMARY KEY пока убран вообще.
 */
public class BlockEntry {

    private String login;                 // TEXT
    private String bchName;               // TEXT

    private int  blockGlobalNumber;       // int32
    private String blockGlobalPreHashe;   // TEXT

    private int  blockLineIndex;          // int16 (храним как int)
    private int  blockLineNumber;         // int32
    private String blockLinePreHashe;     // TEXT

    private int  msgType;                 // int16 (храним как int)

    private byte[] blockByte;             // BLOB

    private String toLogin;               // TEXT nullable
    private String toBchName;             // TEXT
    private int  toBlockGlobalNumber;     // int32
    private String toBlockHashe;          // TEXT

    public BlockEntry() {}

    public BlockEntry(String login,
                      String bchName,
                      int blockGlobalNumber,
                      String blockGlobalPreHashe,
                      int blockLineIndex,
                      int blockLineNumber,
                      String blockLinePreHashe,
                      int msgType,
                      byte[] blockByte,
                      String toLogin,
                      String toBchName,
                      int toBlockGlobalNumber,
                      String toBlockHashe) {
        this.login = login;
        this.bchName = bchName;
        this.blockGlobalNumber = blockGlobalNumber;
        this.blockGlobalPreHashe = blockGlobalPreHashe;
        this.blockLineIndex = blockLineIndex;
        this.blockLineNumber = blockLineNumber;
        this.blockLinePreHashe = blockLinePreHashe;
        this.msgType = msgType;
        this.blockByte = blockByte;
        this.toLogin = toLogin;
        this.toBchName = toBchName;
        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHashe = toBlockHashe;
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getBchName() { return bchName; }
    public void setBchName(String bchName) { this.bchName = bchName; }

    public int getBlockGlobalNumber() { return blockGlobalNumber; }
    public void setBlockGlobalNumber(int blockGlobalNumber) { this.blockGlobalNumber = blockGlobalNumber; }

    public String getBlockGlobalPreHashe() { return blockGlobalPreHashe; }
    public void setBlockGlobalPreHashe(String blockGlobalPreHashe) { this.blockGlobalPreHashe = blockGlobalPreHashe; }

    public int getBlockLineIndex() { return blockLineIndex; }
    public void setBlockLineIndex(int blockLineIndex) { this.blockLineIndex = blockLineIndex; }

    public int getBlockLineNumber() { return blockLineNumber; }
    public void setBlockLineNumber(int blockLineNumber) { this.blockLineNumber = blockLineNumber; }

    public String getBlockLinePreHashe() { return blockLinePreHashe; }
    public void setBlockLinePreHashe(String blockLinePreHashe) { this.blockLinePreHashe = blockLinePreHashe; }

    public int getMsgType() { return msgType; }
    public void setMsgType(int msgType) { this.msgType = msgType; }

    public byte[] getBlockByte() { return blockByte; }
    public void setBlockByte(byte[] blockByte) { this.blockByte = blockByte; }

    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }

    public String getToBchName() { return toBchName; }
    public void setToBchName(String toBchName) { this.toBchName = toBchName; }

    public int getToBlockGlobalNumber() { return toBlockGlobalNumber; }
    public void setToBlockGlobalNumber(int toBlockGlobalNumber) { this.toBlockGlobalNumber = toBlockGlobalNumber; }

    public String getToBlockHashe() { return toBlockHashe; }
    public void setToBlockHashe(String toBlockHashe) { this.toBlockHashe = toBlockHashe; }
}