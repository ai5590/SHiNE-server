// =======================
// shine/db/entities/BlockchainStateEntry.java   (ИЗМЕНЁННАЯ: убраны line0..7, переименовано last_block_*)
// =======================
package shine.db.entities;

import java.util.Base64;

/**
 * Агрегатная сущность текущего состояния блокчейна.
 *
 * ВАЖНО:
 * - Убраны все поля линий line0..7 (они больше не нужны).
 * - Оставляем:
 *    last_block_number
 *    last_block_hash
 *
 * Остальные поля (login, blockchain_key, лимиты) оставлены как в проекте,
 * потому что серверу они реально нужны (ключ подписи/лимит файла).
 */
public final class BlockchainStateEntry {

    private String blockchainName;
    private String login;

    private String blockchainKey; // Base64(32)

    private long sizeLimit;
    private long fileSizeBytes;

    private int lastBlockNumber;     // было last_global_number
    private byte[] lastBlockHash;    // было last_global_hash (nullable)

    private long updatedAtMs;

    public BlockchainStateEntry() {}

    public String getBlockchainName() { return blockchainName; }
    public void setBlockchainName(String blockchainName) { this.blockchainName = blockchainName; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getBlockchainKey() { return blockchainKey; }
    public void setBlockchainKey(String blockchainKey) { this.blockchainKey = blockchainKey; }

    public byte[] getBlockchainKeyBytes() {
        if (blockchainKey == null) return null;
        String s = blockchainKey.trim();
        if (s.isEmpty()) return null;
        try {
            byte[] b = Base64.getDecoder().decode(s);
            return (b != null && b.length == 32) ? b : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public long getSizeLimit() { return sizeLimit; }
    public void setSizeLimit(long sizeLimit) { this.sizeLimit = sizeLimit; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public int getLastBlockNumber() { return lastBlockNumber; }
    public void setLastBlockNumber(int lastBlockNumber) { this.lastBlockNumber = lastBlockNumber; }

    public byte[] getLastBlockHash() { return lastBlockHash; }
    public void setLastBlockHash(byte[] lastBlockHash) { this.lastBlockHash = lastBlockHash; }

    public long getUpdatedAtMs() { return updatedAtMs; }
    public void setUpdatedAtMs(long updatedAtMs) { this.updatedAtMs = updatedAtMs; }
}