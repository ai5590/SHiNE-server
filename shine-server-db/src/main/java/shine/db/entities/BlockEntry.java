// =======================
// shine/db/entities/BlockEntry.java   (ИЗМЕНЁННАЯ под новый blocks формат)
// =======================
package shine.db.entities;

/**
 * Запись блока (таблица blocks) — обновлённая модель под новый формат.
 *
 * Храним:
 *  - login, bch_name (как было в проекте, чтобы не ломать общую БД)
 *  - block_number (глобальный номер в этой цепочке)
 *  - block_bytes (полный блок: preimage + signature)
 *  - block_hash (32 байта вычисленный SHA-256(preimage))
 *  - block_signature (64 байта)
 *
 * Опционально:
 *  - prev_line_number / prev_line_hash / this_line_number
 *
 * Плюс поля индексации (как раньше было удобно):
 *  - msg_type / msg_sub_type
 *  - to_* (если есть target)
 *  - edited_by_block_number (для TEXT_EDIT)
 */
public class BlockEntry {

    private String login;
    private String bchName;

    private int blockNumber;

    private int msgType;
    private int msgSubType;

    private byte[] blockBytes;

    private String toLogin;
    private String toBchName;
    private Integer toBlockNumber;
    private byte[] toBlockHash;

    private byte[] blockHash;
    private byte[] blockSignature;

    private Integer editedByBlockNumber;

    private Integer prevLineNumber;
    private byte[] prevLineHash;
    private Integer thisLineNumber;

    public BlockEntry() {}

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getBchName() { return bchName; }
    public void setBchName(String bchName) { this.bchName = bchName; }

    public int getBlockNumber() { return blockNumber; }
    public void setBlockNumber(int blockNumber) { this.blockNumber = blockNumber; }

    public int getMsgType() { return msgType; }
    public void setMsgType(int msgType) { this.msgType = msgType; }

    public int getMsgSubType() { return msgSubType; }
    public void setMsgSubType(int msgSubType) { this.msgSubType = msgSubType; }

    public byte[] getBlockBytes() { return blockBytes; }
    public void setBlockBytes(byte[] blockBytes) { this.blockBytes = blockBytes; }

    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }

    public String getToBchName() { return toBchName; }
    public void setToBchName(String toBchName) { this.toBchName = toBchName; }

    public Integer getToBlockNumber() { return toBlockNumber; }
    public void setToBlockNumber(Integer toBlockNumber) { this.toBlockNumber = toBlockNumber; }

    public byte[] getToBlockHash() { return toBlockHash; }
    public void setToBlockHash(byte[] toBlockHash) { this.toBlockHash = toBlockHash; }

    public byte[] getBlockHash() { return blockHash; }
    public void setBlockHash(byte[] blockHash) { this.blockHash = blockHash; }

    public byte[] getBlockSignature() { return blockSignature; }
    public void setBlockSignature(byte[] blockSignature) { this.blockSignature = blockSignature; }

    public Integer getEditedByBlockNumber() { return editedByBlockNumber; }
    public void setEditedByBlockNumber(Integer editedByBlockNumber) { this.editedByBlockNumber = editedByBlockNumber; }

    public Integer getPrevLineNumber() { return prevLineNumber; }
    public void setPrevLineNumber(Integer prevLineNumber) { this.prevLineNumber = prevLineNumber; }

    public byte[] getPrevLineHash() { return prevLineHash; }
    public void setPrevLineHash(byte[] prevLineHash) { this.prevLineHash = prevLineHash; }

    public Integer getThisLineNumber() { return thisLineNumber; }
    public void setThisLineNumber(Integer thisLineNumber) { this.thisLineNumber = thisLineNumber; }
}