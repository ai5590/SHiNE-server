package shine.db.entities;

import java.util.Arrays;

/**
 * Агрегатная сущность текущего состояния блокчейна.
 * 1 строка = 1 blockchainName, плюс состояние линий 0..7.
 */
public final class BlockchainStateEntry {

    private String blockchainName;

    private String login;
    private String publicKeyBase64;

    private int sizeLimit;
    /** Размер файла блокчейна в байтах (то, что будем сверять/чинить при старте). */
    private long fileSizeBytes;

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

    public BlockchainStateEntry(String blockchainName,
                                String login,
                                String publicKeyBase64,
                                int sizeLimit,
                                long fileSizeBytes,
                                int lastGlobalNumber,
                                String lastGlobalHash,
                                int[] lastLineNumbers,
                                String[] lastLineHashes,
                                long updatedAtMs) {
        this.blockchainName = blockchainName;
        this.login = login;
        this.publicKeyBase64 = publicKeyBase64;
        this.sizeLimit = sizeLimit;
        this.fileSizeBytes = fileSizeBytes;
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

    public String getBlockchainName() { return blockchainName; }
    public void setBlockchainName(String blockchainName) { this.blockchainName = blockchainName; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getPublicKeyBase64() { return publicKeyBase64; }
    public void setPublicKeyBase64(String publicKeyBase64) { this.publicKeyBase64 = publicKeyBase64; }

    public int getSizeLimit() { return sizeLimit; }
    public void setSizeLimit(int sizeLimit) { this.sizeLimit = sizeLimit; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

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