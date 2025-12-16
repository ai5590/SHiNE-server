package blockchain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * ============================================================================
 *  BchBlockEntry — универсальная запись блокчейна SHiNE (.bch)
 * ============================================================================
 *.
 * 🧩 Формат файла .bch:
 *  Каждый блок хранится последовательно, без промежутков.
 *  Один блок = «заголовок» (RAW) + подпись (64) + хэш (32).
 *.
 *  FULL = RAW + signature(64) + hash(32)
 *.
 * ---------------------------------------------------------------------------
 *  🔹 Структура RAW-части блока (без подписи и хэша)
 * ---------------------------------------------------------------------------
 *  Размеры и порядок строго фиксированы (BigEndian).
 *.
 *  Порядок байтов (сверху вниз, смещения от начала RAW):
 *.
 *  ┌────────────────────────────┬────────┬───────────────────────────────┐
 *  │ Поле                       │ Размер │ Описание                      │
 *  ├────────────────────────────┼────────┼───────────────────────────────┤
 *  │ recordSize                 │ 4 байта│ = M + 20 — общий размер RAW   │
 *  │ recordNumber               │ 4 байта│ порядковый номер блока        │
 *  │ timestamp                  │ 8 байт │ UNIX time (секунды)           │
 *  │ recordType                 │ 2 байта│ тип тела (0=Header, 1=Text)   │
 *  │ recordTypeVersion          │ 2 байта│ версия структуры данного типа │
 *  │ body                       │ M байт │ бинарное тело записи          │
 *  └────────────────────────────┴────────┴───────────────────────────────┘
 *.
 *  ⇒ RAW_HEADER_SIZE = 4 + 4 + 8 + 2 + 2 = 20 байт.
 *  ⇒ recordSize = RAW_HEADER_SIZE + body.length
 *.
 * ---------------------------------------------------------------------------
 *  🔹 Структура FULL-блока
 * ---------------------------------------------------------------------------
 *.
 *  ┌────────────────────────────┬─────────┬──────────────────────────────┐
 *  │ RAW                        │ M+20    │ тело блока без подписи       │
 *  │ signature64                │ 64      │ подпись Ed25519(preimage)    │
 *  │ hash32                     │ 32      │ SHA-256(preimage)            │
 *  └────────────────────────────┴─────────┴──────────────────────────────┘
 *.
 *  ⇒ Общая длина FULL = recordSize + 96 байт.
 *.
 * ---------------------------------------------------------------------------
 *  🔹 Канонический preimage для подписи/хэша
 * ---------------------------------------------------------------------------
 *  preimage = userLogin(UTF-8, без длины) +
 *             blockchainId(8B, BE) +
 *             prevHash32(32B) +
 *             rawBytes (M+20B)
 *.
 *  hash32     = SHA-256(preimage)
 *  signature64= Ed25519.sign(preimage, privateKey)
 *.
 *  Проверка осуществляется через {@link utils.crypto.BchCryptoVerifier}.
 *.
 * ============================================================================
 */
public class BchBlockEntry {

    // ---- Константы типов ----
    public static final short TYPE_HEADER = 0;
    public static final short TYPE_TEXT   = 1;

    // ---- Константы размеров ----
    public static final int SIGNATURE_LEN = 64;
    public static final int HASH_LEN      = 32;
    /** Размер «сырой» шапки без подписи/хэша. */
    public static final int RAW_HEADER_SIZE = 20;

    // ---- Поля RAW-заголовка ----
    public final int   recordSize;        // [4]  M + 20
    public final int   recordNumber;      // [4]  порядковый номер блока
    public final long  timestamp;         // [8]  UNIX time (секунды)
    public final short recordType;        // [2]  тип тела (0=Header, 1=Text)
    public final short recordTypeVersion; // [2]  версия структуры данного типа
    public final byte[] body;             // [M]  тело записи

    // ---- Поля подписи и хэша ----
    private byte[] signature64;           // [64] подпись (Ed25519)
    private byte[] hash32;                // [32] хэш (SHA-256)

    // ---- Кэшированные представления ----
    public final byte[] rawBytes;         // RAW без подписи/хэша
    private byte[] rawBytesWithSignatureAndHash; // FULL (может быть null)

    // ========================================================================
    //                     КОНСТРУКТОР №1 — из полей (RAW only)
    // ========================================================================
    public BchBlockEntry(int recordNumber,
                         long timestamp,
                         short recordType,
                         short recordTypeVersion,
                         byte[] body) {
        Objects.requireNonNull(body, "body == null");

        this.recordNumber      = recordNumber;
        this.timestamp          = timestamp;
        this.recordType         = recordType;
        this.recordTypeVersion  = recordTypeVersion;
        this.body               = Arrays.copyOf(body, body.length);
        this.recordSize         = body.length + RAW_HEADER_SIZE;

        ByteBuffer buf = ByteBuffer
                .allocate(RAW_HEADER_SIZE + body.length)
                .order(ByteOrder.BIG_ENDIAN);

        buf.putInt(recordSize);
        buf.putInt(recordNumber);
        buf.putLong(timestamp);
        buf.putShort(recordType);
        buf.putShort(recordTypeVersion);
        buf.put(body);

        this.rawBytes = buf.array();
    }

    // ========================================================================
    //             КОНСТРУКТОР №2 — из полного массива (RAW + SIG + HASH)
    // ========================================================================
    public BchBlockEntry(byte[] rawWithSigAndHash) {
        Objects.requireNonNull(rawWithSigAndHash, "rawWithSigAndHash == null");
        if (rawWithSigAndHash.length < RAW_HEADER_SIZE + SIGNATURE_LEN + HASH_LEN)
            throw new IllegalArgumentException("Слишком мало данных для полного блока");

        ByteBuffer probe = ByteBuffer.wrap(rawWithSigAndHash).order(ByteOrder.BIG_ENDIAN);
        int rs = probe.getInt(); // recordSize
        if (rs < RAW_HEADER_SIZE)
            throw new IllegalArgumentException("Некорректный recordSize: " + rs);
        if (rawWithSigAndHash.length < rs + SIGNATURE_LEN + HASH_LEN)
            throw new IllegalArgumentException("Данные короче, чем raw+sig+hash");

        this.rawBytes = Arrays.copyOfRange(rawWithSigAndHash, 0, rs);

        ByteBuffer buf = ByteBuffer.wrap(this.rawBytes).order(ByteOrder.BIG_ENDIAN);
        this.recordSize        = buf.getInt();
        this.recordNumber      = buf.getInt();
        this.timestamp         = buf.getLong();
        this.recordType        = buf.getShort();
        this.recordTypeVersion = buf.getShort();

        int bodyLen = this.recordSize - RAW_HEADER_SIZE;
        if (bodyLen < 0 || bodyLen != this.rawBytes.length - RAW_HEADER_SIZE)
            throw new IllegalArgumentException("Неконсистентная длина тела блока");

        this.body = new byte[bodyLen];
        buf.get(this.body);

        // подпись + хэш
        ByteBuffer tail = ByteBuffer
                .wrap(rawWithSigAndHash, rs, SIGNATURE_LEN + HASH_LEN)
                .order(ByteOrder.BIG_ENDIAN);

        this.signature64 = new byte[SIGNATURE_LEN];
        tail.get(this.signature64);
        this.hash32 = new byte[HASH_LEN];
        tail.get(this.hash32);

        this.rawBytesWithSignatureAndHash =
                Arrays.copyOf(rawWithSigAndHash, rs + SIGNATURE_LEN + HASH_LEN);
    }

    // ========================================================================
    //                        Добавить подпись и хэш
    // ========================================================================
    public BchBlockEntry addSignatureAndHash(byte[] signature64, byte[] hash32) {
        Objects.requireNonNull(signature64, "signature64 == null");
        Objects.requireNonNull(hash32, "hash32 == null");
        if (signature64.length != SIGNATURE_LEN) throw new IllegalArgumentException("signature64 != 64");
        if (hash32.length != HASH_LEN) throw new IllegalArgumentException("hash32 != 32");

        this.signature64 = Arrays.copyOf(signature64, SIGNATURE_LEN);
        this.hash32 = Arrays.copyOf(hash32, HASH_LEN);

        byte[] full = new byte[this.rawBytes.length + SIGNATURE_LEN + HASH_LEN];
        System.arraycopy(this.rawBytes, 0, full, 0, this.rawBytes.length);
        System.arraycopy(this.signature64, 0, full, this.rawBytes.length, SIGNATURE_LEN);
        System.arraycopy(this.hash32, 0, full, this.rawBytes.length + SIGNATURE_LEN, HASH_LEN);
        this.rawBytesWithSignatureAndHash = full;
        return this;
    }

    // ========================================================================
    //                              Геттеры
    // ========================================================================
    public String getBodyAsText() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public byte[] getSignature64() { return signature64 == null ? null : Arrays.copyOf(signature64, SIGNATURE_LEN); }
    public byte[] getHash32()      { return hash32 == null ? null : Arrays.copyOf(hash32, HASH_LEN); }
    public byte[] getRawBytesWithSignatureAndHash() {
        return rawBytesWithSignatureAndHash == null ? null : Arrays.copyOf(rawBytesWithSignatureAndHash, rawBytesWithSignatureAndHash.length);
    }

    // ========================================================================
    //                              Отладка
    // ========================================================================
    @Override
    public String toString() {
        return String.format(
                "BchBlock[num=%d, type=%d, ver=%d, time=%d, raw=%d, full=%s]",
                recordNumber, recordType, recordTypeVersion, timestamp,
                rawBytes.length,
                rawBytesWithSignatureAndHash == null ? "null" : String.valueOf(rawBytesWithSignatureAndHash.length)
        );
    }
}
