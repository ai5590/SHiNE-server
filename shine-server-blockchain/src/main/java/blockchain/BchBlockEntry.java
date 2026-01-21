package blockchain;

import blockchain.body.BodyRecord;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * BchBlockEntry — универсальный блок нового формата.
 *
 * RAW (BigEndian) = preimage:
 *   [32] prevHash32       (SHA-256)  hash предыдущего блока (цепочка)
 *   [4]  blockSize        (int)      = размер preimage (в байтах), БЕЗ signature64
 *   [4]  blockNumber      (int)      глобальный номер блока (>=0)
 *   [8]  timestamp        (long)     unix seconds
 *
 *   [2]  type             (short)    тип сообщения
 *   [2]  subType          (short)    подтип сообщения
 *   [2]  version          (short)    версия формата сообщения
 *
 *   [N]  bodyBytes        (bytes)    тело сообщения (БЕЗ type/subType/version)
 *
 * TAIL (НЕ входит в blockSize):
 *   [64] signature64 (Ed25519)      подпись над hash32
 *
 * hash32 ВНУТРИ БЛОКА НЕ ХРАНИМ.
 * hash32 вычисляется при парсинге:
 *   preimage = первые blockSize байт
 *   hash32   = SHA-256(preimage)
 */
public final class BchBlockEntry {

    public static final int SIGNATURE_LEN = 64;
    public static final int HASH_LEN = 32;

    /**
     * Максимальный допустимый размер блока (preimage+signature), чтобы не уложить сервер по памяти/диску.
     * 4 МБ — нормальный “потолок” под тексты/метаданные, и при этом защищает от мусора/атаки.
     */
    public static final int MAX_BLOCK_FULL_BYTES = 4 * 1024 * 1024;

    /**
     * Насколько блок может “обгонять” текущее время (защита от кривых часов/вбросов).
     * Если timestamp больше now + 60 сек — блок считаем неверным.
     */
    public static final long MAX_FUTURE_SECONDS = 60;

    /** Размер фиксированного RAW-заголовка без body */
    public static final int RAW_HEADER_SIZE =
            32   // prevHash32
            + 4  // blockSize
            + 4  // blockNumber
            + 8  // timestamp
            + 2  // type
            + 2  // subType
            + 2; // version

    // --- HEADER (RAW) ---
    public final byte[] prevHash32;   // 32
    public final int blockSize;       // preimage size
    public final int blockNumber;     // >=0
    public final long timestamp;
    public final short type;
    public final short subType;
    public final short version;

    // --- BODY (RAW) ---
    public final byte[] bodyBytes;

    /** Распарсенное тело (создаётся сразу при парсинге блока). */
    public final BodyRecord body;

    // --- TAIL ---
    private final byte[] signature64; // 64

    // --- derived ---
    private final byte[] hash32;      // 32, computed
    private final byte[] preimage;    // blockSize bytes
    private final byte[] fullBytes;   // preimage + signature

    /* ===================================================================== */
    /* ====================== Конструктор из байт ========================== */
    /* ===================================================================== */

    public BchBlockEntry(byte[] fullBytes) {
        Objects.requireNonNull(fullBytes, "fullBytes == null");

        if (fullBytes.length < RAW_HEADER_SIZE + SIGNATURE_LEN) {
            throw new IllegalArgumentException("Block too short");
        }
        if (fullBytes.length > MAX_BLOCK_FULL_BYTES) {
            throw new IllegalArgumentException("Block too large: " + fullBytes.length + " > " + MAX_BLOCK_FULL_BYTES);
        }

        ByteBuffer bb = ByteBuffer.wrap(fullBytes).order(ByteOrder.BIG_ENDIAN);

        this.prevHash32 = new byte[32];
        bb.get(this.prevHash32);

        this.blockSize = bb.getInt();
        if (blockSize < RAW_HEADER_SIZE) {
            throw new IllegalArgumentException("blockSize too small: " + blockSize);
        }
        if (blockSize + SIGNATURE_LEN != fullBytes.length) {
            throw new IllegalArgumentException("blockSize mismatch: blockSize=" + blockSize + " fullLen=" + fullBytes.length);
        }
        if (blockSize + SIGNATURE_LEN > MAX_BLOCK_FULL_BYTES) {
            throw new IllegalArgumentException("Block too large by blockSize: " + (blockSize + SIGNATURE_LEN) + " > " + MAX_BLOCK_FULL_BYTES);
        }

        this.blockNumber = bb.getInt();
        if (this.blockNumber < 0) {
            throw new IllegalArgumentException("blockNumber < 0: " + this.blockNumber);
        }

        this.timestamp = bb.getLong();

        // запрет “в будущее” больше чем на 1 минуту
        long now = Instant.now().getEpochSecond();
        if (this.timestamp > now + MAX_FUTURE_SECONDS) {
            throw new IllegalArgumentException("timestamp is too far in future: ts=" + this.timestamp + " now=" + now + " maxFutureSec=" + MAX_FUTURE_SECONDS);
        }

        this.type = bb.getShort();
        this.subType = bb.getShort();
        this.version = bb.getShort();

        int bodyLen = blockSize - RAW_HEADER_SIZE;
        if (bodyLen < 0) throw new IllegalArgumentException("Invalid body length: " + bodyLen);

        this.bodyBytes = new byte[bodyLen];
        bb.get(this.bodyBytes);

        this.signature64 = new byte[SIGNATURE_LEN];
        bb.get(this.signature64);

        // preimage = первые blockSize байт
        this.preimage = Arrays.copyOfRange(fullBytes, 0, blockSize);

        // hash32 = sha256(preimage)
        this.hash32 = BchCryptoVerifier.sha256(preimage);

        // parse body по header.type/subType/version + ОБЯЗАТЕЛЬНЫЙ check()
        this.body = BodyRecordParser.parse(this.type, this.subType, this.version, this.bodyBytes);

        this.fullBytes = Arrays.copyOf(fullBytes, fullBytes.length);

        // запрет мусора
        if (bb.remaining() != 0) {
            throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
        }
    }

    /* ===================================================================== */
    /* ====================== Конструктор сборки ============================ */
    /* ===================================================================== */

    public BchBlockEntry(byte[] prevHash32,
                         int blockNumber,
                         long timestamp,
                         short type,
                         short subType,
                         short version,
                         byte[] bodyBytes,
                         byte[] signature64) {

        Objects.requireNonNull(prevHash32, "prevHash32 == null");
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        Objects.requireNonNull(signature64, "signature64 == null");

        if (prevHash32.length != 32) throw new IllegalArgumentException("prevHash32 != 32");
        if (signature64.length != SIGNATURE_LEN) throw new IllegalArgumentException("signature64 != 64");

        if (blockNumber < 0) {
            throw new IllegalArgumentException("blockNumber < 0: " + blockNumber);
        }

        // запрет “в будущее” больше чем на 1 минуту
        long now = Instant.now().getEpochSecond();
        if (timestamp > now + MAX_FUTURE_SECONDS) {
            throw new IllegalArgumentException("timestamp is too far in future: ts=" + timestamp + " now=" + now + " maxFutureSec=" + MAX_FUTURE_SECONDS);
        }

        this.prevHash32 = Arrays.copyOf(prevHash32, 32);
        this.blockNumber = blockNumber;
        this.timestamp = timestamp;
        this.type = type;
        this.subType = subType;
        this.version = version;
        this.bodyBytes = Arrays.copyOf(bodyBytes, bodyBytes.length);
        this.signature64 = Arrays.copyOf(signature64, SIGNATURE_LEN);

        this.blockSize = RAW_HEADER_SIZE + this.bodyBytes.length;

        int fullLen = this.blockSize + SIGNATURE_LEN;
        if (fullLen > MAX_BLOCK_FULL_BYTES) {
            throw new IllegalArgumentException("Block too large: " + fullLen + " > " + MAX_BLOCK_FULL_BYTES);
        }

        // parse body по header + ОБЯЗАТЕЛЬНЫЙ check()
        this.body = BodyRecordParser.parse(this.type, this.subType, this.version, this.bodyBytes);

        // build preimage
        ByteBuffer pre = ByteBuffer.allocate(blockSize).order(ByteOrder.BIG_ENDIAN);
        pre.put(this.prevHash32);
        pre.putInt(this.blockSize);
        pre.putInt(this.blockNumber);
        pre.putLong(this.timestamp);
        pre.putShort(this.type);
        pre.putShort(this.subType);
        pre.putShort(this.version);
        pre.put(this.bodyBytes);

        this.preimage = pre.array();
        this.hash32 = BchCryptoVerifier.sha256(preimage);

        ByteBuffer full = ByteBuffer.allocate(blockSize + SIGNATURE_LEN).order(ByteOrder.BIG_ENDIAN);
        full.put(this.preimage);
        full.put(this.signature64);
        this.fullBytes = full.array();
    }

    public byte[] getPreimageBytes() {
        return Arrays.copyOf(preimage, preimage.length);
    }

    public byte[] getSignature64() {
        return Arrays.copyOf(signature64, SIGNATURE_LEN);
    }

    public byte[] getHash32() {
        return Arrays.copyOf(hash32, HASH_LEN);
    }

    public byte[] toBytes() {
        return Arrays.copyOf(fullBytes, fullBytes.length);
    }

    @Override
    public String toString() {
        String timeIso;
        try {
            timeIso = Instant.ofEpochSecond(timestamp).toString();
        } catch (Exception e) {
            timeIso = "некорректныйTimestamp";
        }

        return "BchBlockEntry{"
                + "HDR{"
                + "blockSize=" + blockSize
                + ", blockNumber=" + blockNumber
                + ", timestamp=" + timestamp + " (" + timeIso + ")"
                + ", type=" + (type & 0xFFFF)
                + ", subType=" + (subType & 0xFFFF)
                + ", version=" + (version & 0xFFFF)
                + ", prevHash32(hex)=" + toHex(prevHash32)
                + "}"
                + ", BODY{len=" + (bodyBytes == null ? -1 : bodyBytes.length) + "}"
                + ", TAIL{signature64(hex)=" + toHex(signature64) + "}"
                + ", DERIVED{hash32(hex)=" + toHex(hash32) + "}"
                + "}";
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null) return "null";
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}