package blockchain;

import blockchain.body.BodyRecord;
import blockchain.body.BodyRecordParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * BchBlockEntry_new — универсальный блок нового формата.
 *
 * RAW (BigEndian):
 *   [4]  recordSize        (int)  = размер RAW (включая этот заголовок), БЕЗ signature+hash
 *   [4]  recordNumber      (int)  глобальный номер блока
 *   [8]  timestamp         (long) unix seconds
 *   [2]  lineIndex         (short)
 *   [4]  lineNumber        (int)
 *   [N]  bodyBytes         (body, начинается с [type][version])
 *
 * TAIL (НЕ входит в recordSize):
 *   [64] signature64 (Ed25519)
 *   [32] hash32      (SHA-256)
 */
public final class BchBlockEntry {

    public static final int SIGNATURE_LEN = 64;
    public static final int HASH_LEN = 32;

    /** Размер фиксированного RAW-заголовка без body */
    public static final int RAW_HEADER_SIZE = 4 + 4 + 8 + 2 + 4;

    // --- RAW ---
    public final int recordSize;     // только RAW, без signature+hash
    public final int recordNumber;
    public final long timestamp;
    public final short lineIndex;
    public final int lineNumber;
    public final byte[] bodyBytes;

    /** Распарсенное тело (создаётся сразу при парсинге блока). */
    public final BodyRecord body;

    // --- TAIL ---
    private final byte[] signature64;
    private final byte[] hash32;

    // --- cached ---
    private final byte[] fullBytes;

    /* ===================================================================== */
    /* ====================== Конструктор из байт ========================== */
    /* ===================================================================== */

    public BchBlockEntry(byte[] fullBytes) {
        Objects.requireNonNull(fullBytes, "fullBytes == null");
        if (fullBytes.length < RAW_HEADER_SIZE + SIGNATURE_LEN + HASH_LEN)
            throw new IllegalArgumentException("Block too short");

        ByteBuffer bb = ByteBuffer.wrap(fullBytes).order(ByteOrder.BIG_ENDIAN);

        this.recordSize = bb.getInt();
        if (recordSize + SIGNATURE_LEN + HASH_LEN != fullBytes.length)
            throw new IllegalArgumentException("recordSize mismatch");

        this.recordNumber = bb.getInt();
        this.timestamp = bb.getLong();
        this.lineIndex = bb.getShort();
        this.lineNumber = bb.getInt();

        int bodyLen = recordSize - RAW_HEADER_SIZE;
        if (bodyLen <= 0)
            throw new IllegalArgumentException("Invalid body length");

        this.bodyBytes = new byte[bodyLen];
        bb.get(this.bodyBytes);

        // ✅ Сразу парсим BodyRecord (и если неизвестный type/version — тут же упадём)
        this.body = BodyRecordParser.parse(this.bodyBytes);

        // ✅ УРОВЕНЬ B: проверка ожидаемой линии по типу body
        short expectedLine = this.body.expectedLineIndex();
        if (this.lineIndex != expectedLine) {
            throw new IllegalArgumentException(
                    "Body is in wrong lineIndex: expected=" + expectedLine + " actual=" + this.lineIndex +
                            " (type=" + this.body.type() + " ver=" + this.body.version() + ")"
            );
        }

        this.signature64 = new byte[SIGNATURE_LEN];
        bb.get(this.signature64);

        this.hash32 = new byte[HASH_LEN];
        bb.get(this.hash32);

        this.fullBytes = Arrays.copyOf(fullBytes, fullBytes.length);
    }

    /* ===================================================================== */
    /* ====================== Конструктор сборки ============================ */
    /* ===================================================================== */

    public BchBlockEntry(int recordNumber,
                         long timestamp,
                         short lineIndex,
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
        this.lineIndex = lineIndex;
        this.lineNumber = lineNumber;
        this.bodyBytes = Arrays.copyOf(bodyBytes, bodyBytes.length);

        // ✅ И при сборке — тоже сразу парсим body (чтобы объект был цельным)
        this.body = BodyRecordParser.parse(this.bodyBytes);

        // ✅ УРОВЕНЬ B: проверка ожидаемой линии по типу body
        short expectedLine = this.body.expectedLineIndex();
        if (this.lineIndex != expectedLine) {
            throw new IllegalArgumentException(
                    "Body is in wrong lineIndex: expected=" + expectedLine + " actual=" + this.lineIndex +
                            " (type=" + this.body.type() + " ver=" + this.body.version() + ")"
            );
        }

        this.signature64 = Arrays.copyOf(signature64, SIGNATURE_LEN);
        this.hash32 = Arrays.copyOf(hash32, HASH_LEN);

        // recordSize теперь только RAW (header + body), без signature+hash
        this.recordSize = RAW_HEADER_SIZE + bodyBytes.length;

        int fullLen = this.recordSize + SIGNATURE_LEN + HASH_LEN;

        ByteBuffer bb = ByteBuffer.allocate(fullLen).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(this.recordSize);
        bb.putInt(recordNumber);
        bb.putLong(timestamp);
        bb.putShort(lineIndex);
        bb.putInt(lineNumber);
        bb.put(bodyBytes);
        bb.put(this.signature64);
        bb.put(this.hash32);

        this.fullBytes = bb.array();
    }

    public byte[] getRawBytes() {
        int rawLen = recordSize; // ровно RAW, без signature+hash
        byte[] raw = new byte[rawLen];
        System.arraycopy(fullBytes, 0, raw, 0, rawLen);
        return raw;
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
                + "RAW{"
                + "recordSize=" + recordSize
                + ", recordNumber=" + recordNumber
                + ", timestamp=" + timestamp + " (" + timeIso + ")"
                + ", lineIndex=" + lineIndex
                + ", lineNumber=" + lineNumber
                + ", bodyLen=" + (bodyBytes == null ? -1 : bodyBytes.length)
                + ", bodyType=" + (body == null ? "?" : (body.type() & 0xFFFF))
                + ", bodyVer=" + (body == null ? "?" : (body.version() & 0xFFFF))
                + "}"
                + ", TAIL{"
                + "signature64(hex)=" + toHex(signature64)
                + ", hash32(hex)=" + toHex(hash32)
                + "}"
                + ", FULL{"
                + "fullLen=" + (fullBytes == null ? -1 : fullBytes.length)
                + ", rawLen=" + recordSize
                + "}"
                + ", body=" + (body == null ? "null" : body.toString())
                + ", bodyBytesPreview(hex32)=" + toHexPreview(bodyBytes, 32)
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

    private static String toHexPreview(byte[] bytes, int maxBytes) {
        if (bytes == null) return "null";
        if (maxBytes <= 0) return "";
        int n = Math.min(bytes.length, maxBytes);
        byte[] cut = Arrays.copyOf(bytes, n);
        String hex = toHex(cut);
        if (bytes.length > n) hex += "…(+" + (bytes.length - n) + " байт)";
        return hex;
    }
}