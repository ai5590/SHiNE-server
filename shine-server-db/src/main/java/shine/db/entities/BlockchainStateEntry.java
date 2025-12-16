package shine.db.entities;

import java.util.Arrays;

/**
 * Агрегатная сущность текущего состояния блокчейна.
 * 1 строка = 1 blockchainId, плюс состояние линий 0..7.
 */
public final class BlockchainStateEntry {

    private long blockchainId;

    private String userLogin;
    private String publicKeyBase64;

    private int sizeLimit;
    private int sizeBytes;

    private int lastGlobalNumber;
    private String lastGlobalHash; // HEX(64) либо пустая строка для "нулевого"

    /** line 0..7 */
    private final int[] lastLineNumbers = new int[8];
    /** line 0..7 */
    private final String[] lastLineHashes = new String[8];

    private long updatedAtMs;

    public BlockchainStateEntry() {
        // по умолчанию хэши пустые (как "0")
        for (int i = 0; i < 8; i++) lastLineHashes[i] = "";
        this.lastGlobalHash = "";
    }

    // --- удобный конструктор (если хочешь) ---
    public BlockchainStateEntry(long blockchainId,
                                String userLogin,
                                String publicKeyBase64,
                                int sizeLimit,
                                int sizeBytes,
                                int lastGlobalNumber,
                                String lastGlobalHash,
                                int[] lastLineNumbers,
                                String[] lastLineHashes,
                                long updatedAtMs) {
        this.blockchainId = blockchainId;
        this.userLogin = userLogin;
        this.publicKeyBase64 = publicKeyBase64;
        this.sizeLimit = sizeLimit;
        this.sizeBytes = sizeBytes;
        this.lastGlobalNumber = lastGlobalNumber;
        this.lastGlobalHash = lastGlobalHash == null ? "" : lastGlobalHash;

        if (lastLineNumbers != null) {
            if (lastLineNumbers.length != 8) throw new IllegalArgumentException("lastLineNumbers must be len=8");
            System.arraycopy(lastLineNumbers, 0, this.lastLineNumbers, 0, 8);
        }
        if (lastLineHashes != null) {
            if (lastLineHashes.length != 8) throw new IllegalArgumentException("lastLineHashes must be len=8");
            for (int i = 0; i < 8; i++) this.lastLineHashes[i] = lastLineHashes[i] == null ? "" : lastLineHashes[i];
        } else {
            for (int i = 0; i < 8; i++) this.lastLineHashes[i] = "";
        }

        this.updatedAtMs = updatedAtMs;
    }

    // --- getters / setters ---

    public long getBlockchainId() { return blockchainId; }
    public void setBlockchainId(long blockchainId) { this.blockchainId = blockchainId; }

    public String getUserLogin() { return userLogin; }
    public void setUserLogin(String userLogin) { this.userLogin = userLogin; }

    public String getPublicKeyBase64() { return publicKeyBase64; }
    public void setPublicKeyBase64(String publicKeyBase64) { this.publicKeyBase64 = publicKeyBase64; }

    public int getSizeLimit() { return sizeLimit; }
    public void setSizeLimit(int sizeLimit) { this.sizeLimit = sizeLimit; }

    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int sizeBytes) { this.sizeBytes = sizeBytes; }

    public int getLastGlobalNumber() { return lastGlobalNumber; }
    public void setLastGlobalNumber(int lastGlobalNumber) { this.lastGlobalNumber = lastGlobalNumber; }

    public String getLastGlobalHash() { return lastGlobalHash; }
    public void setLastGlobalHash(String lastGlobalHash) { this.lastGlobalHash = lastGlobalHash == null ? "" : lastGlobalHash; }

    /** line in [0..7] */
    public int getLastLineNumber(int line) {
        checkLine(line);
        return lastLineNumbers[line];
    }
    public void setLastLineNumber(int line, int value) {
        checkLine(line);
        lastLineNumbers[line] = value;
    }

    /** line in [0..7] */
    public String getLastLineHash(int line) {
        checkLine(line);
        return lastLineHashes[line];
    }
    public void setLastLineHash(int line, String value) {
        checkLine(line);
        lastLineHashes[line] = value == null ? "" : value;
    }

    public int[] getLastLineNumbersCopy() {
        return Arrays.copyOf(lastLineNumbers, 8);
    }
    public String[] getLastLineHashesCopy() {
        return Arrays.copyOf(lastLineHashes, 8);
    }

    public long getUpdatedAtMs() { return updatedAtMs; }
    public void setUpdatedAtMs(long updatedAtMs) { this.updatedAtMs = updatedAtMs; }

    private static void checkLine(int line) {
        if (line < 0 || line > 7) throw new IllegalArgumentException("line must be 0..7");
    }
}