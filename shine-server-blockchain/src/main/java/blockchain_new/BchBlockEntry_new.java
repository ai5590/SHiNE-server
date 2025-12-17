package blockchain_new;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/**
 * BchBlockEntry_new — универсальный блок нового формата.
 *
 * RAW (BigEndian):
 *   [4]  recordSize        (int)  = RAW + signature + hash
 *   [4]  recordNumber      (int)  глобальный номер блока
 *   [8]  timestamp         (long) unix seconds
 *   [2]  line              (short)
 *   [4]  lineNumber        (int)
 *   [N]  bodyBytes         (body, начинается с [type][version])
 *
 * TAIL:
 *   [64] signature64 (Ed25519)
 *   [32] hash32      (SHA-256)
 */
public final class BchBlockEntry_new {

    public static final int SIGNATURE_LEN = 64;
    public static final int HASH_LEN = 32;

    /** Размер фиксированного RAW-заголовка без body */
    public static final int RAW_HEADER_SIZE = 4 + 4 + 8 + 2 + 4;

    // --- RAW ---
    public final int recordSize;
    public final int recordNumber;
    public final long timestamp;
    public final short line;
    public final int lineNumber;
    public final byte[] bodyBytes;

    // --- TAIL ---
    private final byte[] signature64;
    private final byte[] hash32;

    // --- cached ---
    private final byte[] fullBytes;

    /* ===================================================================== */
    /* ====================== Конструктор из байт ========================== */
    /* ===================================================================== */

    public BchBlockEntry_new(byte[] fullBytes) {
        Objects.requireNonNull(fullBytes, "fullBytes == null");
        if (fullBytes.length < RAW_HEADER_SIZE + SIGNATURE_LEN + HASH_LEN)
            throw new IllegalArgumentException("Block too short");

        ByteBuffer bb = ByteBuffer.wrap(fullBytes).order(ByteOrder.BIG_ENDIAN);

        this.recordSize = bb.getInt();
        if (recordSize != fullBytes.length)
            throw new IllegalArgumentException("recordSize mismatch");

        this.recordNumber = bb.getInt();
        this.timestamp = bb.getLong();
        this.line = bb.getShort();
        this.lineNumber = bb.getInt();

        int bodyLen = recordSize - RAW_HEADER_SIZE - SIGNATURE_LEN - HASH_LEN;
        if (bodyLen <= 0)
            throw new IllegalArgumentException("Invalid body length");

        this.bodyBytes = new byte[bodyLen];
        bb.get(this.bodyBytes);

        this.signature64 = new byte[SIGNATURE_LEN];
        bb.get(this.signature64);

        this.hash32 = new byte[HASH_LEN];
        bb.get(this.hash32);

        this.fullBytes = Arrays.copyOf(fullBytes, fullBytes.length);
    }

    /* ===================================================================== */
    /* ====================== Конструктор сборки ============================ */
    /* ===================================================================== */

    public BchBlockEntry_new(int recordNumber,
                             long timestamp,
                             short line,
                             int lineNumber,
                             byte[] bodyBytes,
                             byte[] signature64,
                             byte[] hash32) {

        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        Objects.requireNonNull(signature64, "signature64 == null");
        Objects.requireNonNull(hash32, "hash32 == null");

        if (signature64.length != SIGNATURE_LEN)
            throw new IllegalArgumentException("signature64 != 64");
        if (hash32.length != HASH_LEN)
            throw new IllegalArgumentException("hash32 != 32");

        this.recordNumber = recordNumber;
        this.timestamp = timestamp;
        this.line = line;
        this.lineNumber = lineNumber;
        this.bodyBytes = Arrays.copyOf(bodyBytes, bodyBytes.length);
        this.signature64 = Arrays.copyOf(signature64, SIGNATURE_LEN);
        this.hash32 = Arrays.copyOf(hash32, HASH_LEN);

        this.recordSize =
                RAW_HEADER_SIZE +
                bodyBytes.length +
                SIGNATURE_LEN +
                HASH_LEN;

        ByteBuffer bb = ByteBuffer.allocate(recordSize).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(recordSize);
        bb.putInt(recordNumber);
        bb.putLong(timestamp);
        bb.putShort(line);
        bb.putInt(lineNumber);
        bb.put(bodyBytes);
        bb.put(this.signature64);
        bb.put(this.hash32);

        this.fullBytes = bb.array();
    }


    public byte[] getRawBytes() {
        int rawLen = recordSize - SIGNATURE_LEN - HASH_LEN;
        byte[] raw = new byte[rawLen];
        System.arraycopy(fullBytes, 0, raw, 0, rawLen);
        return raw;
    }

    /* ===================================================================== */

    public byte[] getSignature64() {
        return Arrays.copyOf(signature64, SIGNATURE_LEN);
    }

    public byte[] getHash32() {
        return Arrays.copyOf(hash32, HASH_LEN);
    }

    public byte[] toBytes() {
        return Arrays.copyOf(fullBytes, fullBytes.length);
    }
}