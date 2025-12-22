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

package blockchain;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.blockchain.BchInfoEntry;
import utils.crypto.BchCryptoVerifier;

/**
 * BchBlockValidator — проверяет корректность блока:
 *   1) последовательность номеров блоков в цепочке;
 *   2) криптографическую целостность (подпись и хэш).
 *.
 * Не проверяет:
 *   - структуру и содержимое body;
 *   - поля HEADER и логин (этим занимаются другие проверки).
 */
public final class BchBlockValidator {

    private static final Logger log = LoggerFactory.getLogger(BchBlockValidator.class);

    private BchBlockValidator() {}

    /**
     * Проверяет, что блок может быть корректно добавлен к цепочке.
     *
     * Не используется при получении запроса на добавление блока по сети (тк там возвращаются более протоколо осмысленные коды
     * если блок не подходит по номеру.
     *
     * А этот класс может быть использован в будущем для внутренних, повторных проверок существующих цепочек блоков.
     *
     * @param block   блок (распарсенный из байт)
     * @param chain   информация о цепочке (BchInfoEntry)
     * @param chainId идентификатор цепочки
     * @return true если порядок и криптография корректны, иначе false
     */
    public static boolean validate(BchBlockEntry block, BchInfoEntry chain, long chainId) {
        if (block == null || chain == null) {
            log.warn("❌ Ошибка: блок или данные о цепочке не переданы");
            return false;
        }

        // 1️⃣ Проверка последовательности номера
        int expectedNumber = chain.lastBlockNumber + 1;
        if (block.recordNumber < expectedNumber) {
            log.warn("❌ Блок с номером {} уже существует (ожидался {})", block.recordNumber, expectedNumber);
            return false;
        }
        if (block.recordNumber > expectedNumber) {
            log.warn("❌ Нарушена последовательность: получен блок {}, ожидался {}", block.recordNumber, expectedNumber);
            return false;
        }

        // 2️⃣ Проверка публичного ключа
        byte[] publicKey = chain.getPublicKey32();
        if (publicKey == null || publicKey.length != 32) {
            log.warn("❌ В цепочке отсутствует корректный публичный ключ (chainId={})", chainId);
            return false;
        }

        // 3️⃣ Получаем предыдущий хэш
        byte[] prevHash32 = hexToBytes(chain.lastBlockHash);

        // 4️⃣ Проверка подписи и хэша
        try {
            boolean ok = BchCryptoVerifier.verifyAll(
                    chain.userLogin,
                    chainId,
                    prevHash32,
                    block.rawBytes,
                    block.getSignature64(),
                    block.getHash32(),
                    publicKey
            );

            if (!ok) {
                log.warn("❌ Криптографическая проверка не пройдена: хэш или подпись не совпадают (chainId={}, blockNum={})",
                        chainId, block.recordNumber);
                return false;
            }

            log.info("✅ Блок {} успешно прошёл проверку подписи и хэша (chainId={})",
                    block.recordNumber, chainId);
            return true;

        } catch (Exception e) {
            log.error("❌ Исключение при проверке блока (chainId={}): {}", chainId, e.getMessage());
            return false;
        }
    }

    // -------------------- HEX → байты --------------------

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[32]; // пустой хэш = 32 нуля
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }
}

package blockchain.body;

/**
 * BodyRecord_new — общий контракт для всех типов body (тела блока).
 *
 * Идея:
 *  - На каждый тип body (Header, Text, File, ...) — отдельный класс.
 *  - Десериализация из байтов делается КОНСТРУКТОРОМ:
 *      new XxxBody_new(byte[] bodyBytes)
 *    (конструктор обязан распарсить байты или кинуть IllegalArgumentException).
 *
 *  - Валидация делается методом check().
 *    check() должен:
 *      - вернуть this, если всё корректно
 *      - кинуть IllegalArgumentException, если данные некорректны
 *
 *  - Сериализация обратно в байты делается методом toBytes().
 *
 *  - type() и version() — это идентификаторы формата body.
 *    Они должны быть константами для класса (например TYPE=1, VERSION=1).
 */
public interface BodyRecord {

    /** Код типа записи (совпадает с recordType в BchBlockEntry). */
    short type();

    /** Версия формата записи (совпадает с recordTypeVersion в BchBlockEntry). */
    short version();

    /** Проверить корректность содержимого и вернуть этот объект (или кинуть исключение). */
    BodyRecord check();

    /**
     * Сериализовать тело записи в байты (ровно то, что кладётся в block.body).
     * Важно: НЕ включает общий заголовок блока (recordNumber/timestamp/type/version).
     */
    byte[] toBytes();
}

package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * BodyRecordParser_new — общий фабричный парсер body для нового формата.
 *
 * Правило совместимости (строгое):
 * - если (type, version) неизвестны → кидаем IllegalArgumentException
 */
public final class BodyRecordParser {

    private BodyRecordParser() {}

    public static BodyRecord parse(byte[] bodyBytes) {
        if (bodyBytes == null) throw new IllegalArgumentException("bodyBytes == null");
        if (bodyBytes.length < 4) throw new IllegalArgumentException("bodyBytes too short (<4)");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);
        short type = bb.getShort();
        short ver  = bb.getShort();

        // Строгое сопоставление type+version → класс
        int key = ((type & 0xFFFF) << 16) | (ver & 0xFFFF);

        return switch (key) {
            case 0x0000_0001 -> new HeaderBody(bodyBytes); // type=0, ver=1
            case 0x0001_0001 -> new TextBody(bodyBytes);   // type=1, ver=1
            default -> throw new IllegalArgumentException(String.format(
                    "Unknown body type/version: type=%d ver=%d (key=0x%08X)",
                    (type & 0xFFFF), (ver & 0xFFFF), key
            ));
        };
    }
}
package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * HeaderBody_new — type=0, version=1.
 *
 * Полный bodyBytes:
 *   [2] type=0
 *   [2] version=1
 *   [payload...]
 *
 * Payload (как у текущего HeaderBody):
 *   [8]  tag ASCII "SHiNE001"
 *   [8]  blockchainId (long BE)
 *   [1]  loginLength=N (uint8)
 *   [N]  userLogin UTF-8
 *   [4]  blockchainType (int BE)  (резерв)
 *   [4]  blockchainNumber (int BE) (резерв)
 *   [2]  versionUserBch (short BE) (резерв)
 *   [8]  prevUserBchId (long BE)   (резерв)
 *   [32] publicKey32 (raw)
 */
public final class HeaderBody implements BodyRecord {

    public static final short TYPE = 0;
    public static final short VER  = 1;

    public static final String TAG = "SHiNE001";
    public static final int PUBKEY_LEN = 32;

    public final String tag; // "SHiNE001"
    public final long blockchainId;
    public final String userLogin;
    public final int blockchainType;
    public final int blockchainNumber;
    public final short versionUserBch;
    public final long prevUserBchId;
    public final byte[] publicKey32;

    /**
     * Десериализация из полного bodyBytes (ВКЛЮЧАЯ первые 4 байта type/version).
     */
    public HeaderBody(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        if (bodyBytes.length < 4) throw new IllegalArgumentException("HeaderBody_new too short");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);
        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER)
            throw new IllegalArgumentException("Not HeaderBody_new: type=" + type + " ver=" + ver);

        // Теперь bb стоит на payload
        if (bb.remaining() < 8 + 8 + 1 + 4 + 4 + 2 + 8 + 32)
            throw new IllegalArgumentException("Header payload too short");

        byte[] tagBytes = new byte[8];
        bb.get(tagBytes);
        String t = new String(tagBytes, StandardCharsets.US_ASCII);
        if (!TAG.equals(t)) throw new IllegalArgumentException("Bad tag: " + t);
        this.tag = t;

        this.blockchainId = bb.getLong();

        int loginLen = Byte.toUnsignedInt(bb.get());
        if (loginLen <= 0 || bb.remaining() < loginLen + 4 + 4 + 2 + 8 + 32)
            throw new IllegalArgumentException("Bad login length");

        byte[] loginBytes = new byte[loginLen];
        bb.get(loginBytes);
        this.userLogin = new String(loginBytes, StandardCharsets.UTF_8);

        this.blockchainType   = bb.getInt();
        this.blockchainNumber = bb.getInt();
        this.versionUserBch   = bb.getShort();
        this.prevUserBchId    = bb.getLong();

        this.publicKey32 = new byte[PUBKEY_LEN];
        bb.get(this.publicKey32);
    }

    /**
     * Создание “вручную” (для генерации первого блока).
     */
    public HeaderBody(long blockchainId,
                      String userLogin,
                      int blockchainType,
                      int blockchainNumber,
                      short versionUserBch,
                      long prevUserBchId,
                      byte[] publicKey32) {

        Objects.requireNonNull(userLogin, "userLogin == null");
        Objects.requireNonNull(publicKey32, "publicKey32 == null");
        if (publicKey32.length != PUBKEY_LEN)
            throw new IllegalArgumentException("publicKey32 must be 32 bytes");

        this.tag = TAG;
        this.blockchainId = blockchainId;
        this.userLogin = userLogin;
        this.blockchainType = blockchainType;
        this.blockchainNumber = blockchainNumber;
        this.versionUserBch = versionUserBch;
        this.prevUserBchId = prevUserBchId;
        this.publicKey32 = Arrays.copyOf(publicKey32, PUBKEY_LEN);
    }

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }

    @Override
    public HeaderBody check() {
        if (userLogin == null || userLogin.isBlank())
            throw new IllegalArgumentException("Login is blank");
        if (!userLogin.matches("^[A-Za-z0-9_]+$"))
            throw new IllegalArgumentException("Login must match ^[A-Za-z0-9_]+$");
        if (publicKey32 == null || publicKey32.length != PUBKEY_LEN)
            throw new IllegalArgumentException("publicKey32 must be 32 bytes");
        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] loginUtf8 = userLogin.getBytes(StandardCharsets.UTF_8);
        if (loginUtf8.length > 255)
            throw new IllegalArgumentException("Login too long (>255 bytes)");

        int payloadCap = 8 + 8 + 1 + loginUtf8.length + 4 + 4 + 2 + 8 + 32;
        int cap = 4 + payloadCap;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        // [type/version]
        bb.putShort(TYPE);
        bb.putShort(VER);

        // payload
        bb.put(TAG.getBytes(StandardCharsets.US_ASCII)); // [8]
        bb.putLong(blockchainId);                        // [8]
        bb.put((byte) loginUtf8.length);                 // [1]
        bb.put(loginUtf8);                               // [N]
        bb.putInt(blockchainType);                       // [4]
        bb.putInt(blockchainNumber);                     // [4]
        bb.putShort(versionUserBch);                     // [2]
        bb.putLong(prevUserBchId);                       // [8]
        bb.put(publicKey32);                             // [32]

        return bb.array();
    }
}
package blockchain;

import blockchain.body.BodyRecord;
import blockchain.body.HeaderBody;
import blockchain.body.TextBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ============================================================================
 * BodyRecordParser — универсальный парсер тел (body) блоков .bch
 * ============================================================================
 *.
 * 🧩 Назначение:
 *   Преобразует пару (recordType, recordTypeVersion, bodyBytes)
 *   в конкретный объект, реализующий интерфейс {@link BodyRecord}.
 *.
 * 🔹 Особенность:
 *   Используется объединённый 4-байтовый код:
 *.
 *       fullCode = (recordType << 16) | (recordTypeVersion & 0xFFFF)
 *.
 *   Это позволяет различать версии одного типа блока,
 *   например: TextBody v1, TextBody v2 и т.д.
 *.
 * 🔹 Пример:
 *   BodyRecord body = BodyRecordParser.parse(block.recordType, block.recordTypeVersion, block.body);
 *.
 * ============================================================================
 */
public final class BodyRecordParser {

    private static final Logger log = LoggerFactory.getLogger(BodyRecordParser.class);

    private BodyRecordParser() {}

    /**
     * Распарсить тело блока по типу и версии записи.
     *
     * @param recordType         код типа записи (0 = Header, 1 = Text, ...)
     * @param recordTypeVersion  версия формата записи
     * @param body               массив байт тела записи
     * @return объект, реализующий BodyRecord
     */
    public static BodyRecord parse(short recordType, short recordTypeVersion, byte[] body) {
        if (body == null)
            throw new IllegalArgumentException("body == null");

        // Объединяем тип и версию в 4-байтовый ключ
        int fullCode = ((recordType & 0xFFFF) << 16) | (recordTypeVersion & 0xFFFF);

        switch (fullCode) {

            // ---------------------------------------------------------
            // TYPE 0, VERSION 1 — HeaderBody v1
            // ---------------------------------------------------------
            // Заголовок цепочки пользователя (первый блок).
            //
            // Формат body (без общих 20 байт заголовка блока):
            //  [8]  ASCII tag = "SHiNE001"
            //  [8]  blockchainId (long, BE)
            //  [4]  blockchainType (int, BE)
            //  [4]  blockchainNumber (int, BE)
            //  [1]  userLoginLength = N (unsigned byte)
            //  [N]  userLogin (UTF-8)
            //  [2]  versionUserBch (short, BE)
            //  [8]  prevUserBchId (long, BE)
            //  [32] publicKey32
            //
            // Назначение:
            //   Создаёт новую пользовательскую цепочку (блок №0).
            case (0x0000_0001):
                return new HeaderBody(body);

            // ---------------------------------------------------------
            // TYPE 1, VERSION 1 — TextBody v1
            // ---------------------------------------------------------
            // Простое текстовое сообщение UTF-8.
            //
            // Формат body (без общих 20 байт заголовка блока):
            //   [N] message (UTF-8)
            //
            // Назначение:
            //   Текстовые и системные сообщения, описания, комментарии.
            case (0x0001_0001):
                return new TextBody(body);

            // ---------------------------------------------------------
            // РЕЗЕРВ — будущие типы и версии
            // ---------------------------------------------------------
            // Пример: (0x0001_0002) → TextBody v2 (например, JSON-структура)
            //          (0x0002_0001) → FileBody v1
            //
            // case (0x0001_0002):
            //     return new TextBodyV2(body);
            //
            // case (0x0002_0001):
            //     return new FileBody(body);

            default:
                throw new IllegalArgumentException(String.format(
                        "Неизвестный тип блока: type=%d version=%d (fullCode=0x%08X)",
                        recordType, recordTypeVersion, fullCode));
        }
    }
}

package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * TextBody_new — type=1, version=1.
 *
 * Полный bodyBytes:
 *   [2] type=1
 *   [2] version=1
 *   [payload...]
 *
 * Payload:
 *   UTF-8 bytes (N>0)
 */
public final class TextBody implements BodyRecord {

    public static final short TYPE = 1;
    public static final short VER  = 1;

    public final String message;

    /** Десериализация из полного bodyBytes (включая type/version). */
    public TextBody(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        if (bodyBytes.length < 5) // минимум: 4 байта type/ver + 1 байт текста
            throw new IllegalArgumentException("TextBody_new too short");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);
        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER)
            throw new IllegalArgumentException("Not TextBody_new: type=" + type + " ver=" + ver);

        byte[] payload = new byte[bb.remaining()];
        bb.get(payload);

        // строгая проверка UTF-8
        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            this.message = decoder.decode(ByteBuffer.wrap(payload)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Text payload is not valid UTF-8", e);
        }

        if (this.message.isBlank())
            throw new IllegalArgumentException("Text message is blank");
    }

    /** Создание из строки. */
    public TextBody(String message) {
        Objects.requireNonNull(message, "message == null");
        if (message.isBlank())
            throw new IllegalArgumentException("message is blank");
        this.message = message;
    }

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }

    @Override
    public TextBody check() {
        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Text message is blank");
        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] msg = message.getBytes(StandardCharsets.UTF_8);
        if (msg.length == 0)
            throw new IllegalArgumentException("Text payload is empty");

        ByteBuffer bb = ByteBuffer.allocate(4 + msg.length).order(ByteOrder.BIG_ENDIAN);
        bb.putShort(TYPE);
        bb.putShort(VER);
        bb.put(msg);
        return bb.array();
    }
}
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
package blockchain_new;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

public final class BchCryptoVerifier_new {

    private static final byte[] DOMAIN = "SHiNE".getBytes(StandardCharsets.US_ASCII);

    private BchCryptoVerifier_new() {}

    /**
     * preimage =
     *   "SHiNE" +
     *   [1] loginLen + loginBytes +
     *   prevGlobalHash32 +
     *   prevLineHash32 +
     *   rawBytes
     */
    public static byte[] buildPreimage(String userLogin,
                                       byte[] prevGlobalHash32,
                                       byte[] prevLineHash32,
                                       byte[] rawBytes) {

        Objects.requireNonNull(userLogin, "userLogin == null");
        Objects.requireNonNull(prevGlobalHash32, "prevGlobalHash32 == null");
        Objects.requireNonNull(prevLineHash32, "prevLineHash32 == null");
        Objects.requireNonNull(rawBytes, "rawBytes == null");

        if (prevGlobalHash32.length != 32 || prevLineHash32.length != 32)
            throw new IllegalArgumentException("hash len != 32");

        byte[] loginBytes = userLogin.getBytes(StandardCharsets.UTF_8);
        if (loginBytes.length > 255)
            throw new IllegalArgumentException("login >255 bytes");

        ByteBuffer bb = ByteBuffer.allocate(
                DOMAIN.length +
                1 + loginBytes.length +
                32 + 32 +
                rawBytes.length
        ).order(ByteOrder.BIG_ENDIAN);

        bb.put(DOMAIN);
        bb.put((byte) loginBytes.length);
        bb.put(loginBytes);
        bb.put(prevGlobalHash32);
        bb.put(prevLineHash32);
        bb.put(rawBytes);

        return bb.array();
    }

    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return d.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // TODO: сюда подключается твой Ed25519 util
    public static boolean verifySignature(byte[] hash32,
                                          byte[] signature64,
                                          byte[] publicKey32) {
        // TODO: Ed25519.verify(hash32, signature64, publicKey32)
        return true;
    }
}
package utils.blockchain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Base64;

/**
 * BchInfoEntry — данные об одной цепочке блокчейна.
 * Используется менеджером BchInfoManager.
 */
public final class BchInfoEntry {

    @JsonProperty("blockchainId")
    public final long blockchainId;

    @JsonProperty("userLogin")
    public final String userLogin;

    @JsonProperty("publicKeyBase64")
    public final String publicKeyBase64;

    @JsonProperty("blockchainSizeLimit")
    public final int blockchainSizeLimit;

    @JsonProperty("blockchainSize")
    public final int blockchainSize;

    @JsonProperty("lastBlockNumber")
    public final int lastBlockNumber;

    @JsonProperty("lastBlockHash")
    public final String lastBlockHash;

    @JsonCreator
    public BchInfoEntry(
            @JsonProperty("blockchainId") long blockchainId,
            @JsonProperty("userLogin") String userLogin,
            @JsonProperty("publicKeyBase64") String publicKeyBase64,
            @JsonProperty("blockchainSizeLimit") int blockchainSizeLimit,
            @JsonProperty("blockchainSize") int blockchainSize,
            @JsonProperty("lastBlockNumber") int lastBlockNumber,
            @JsonProperty("lastBlockHash") String lastBlockHash
    ) {
        this.blockchainId = blockchainId;
        this.userLogin = userLogin == null ? "" : userLogin;
        this.publicKeyBase64 = publicKeyBase64;
        this.blockchainSizeLimit = blockchainSizeLimit;
        this.blockchainSize = blockchainSize;
        this.lastBlockNumber = lastBlockNumber;
        this.lastBlockHash = lastBlockHash == null ? "" : lastBlockHash;
    }

    /** Публичный ключ в бинарном виде (32 байта) или null, если битый. */
    public byte[] getPublicKey32() {
        try {
            byte[] raw = Base64.getDecoder().decode(publicKeyBase64);
            return (raw != null && raw.length == 32) ? raw : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

package utils.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BchInfoManager — Singleton.
 *.
 * Держит в памяти информацию обо всех блокчейнах.
 * Сейчас читает и пишет JSON на диск (data/blockchain_info.json).
 * В будущем можно заменить на SQL без изменений в остальном коде.
 */
public final class BchInfoManager {

    private static final Logger log = LoggerFactory.getLogger(BchInfoManager.class);

    private static final String FILE_NAME = "blockchain_info.json";
    private static final Path   DATA_DIR  = Paths.get("data");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static BchInfoManager instance;

    /** blockchainId → запись о цепочке */
    private final Map<Long, BchInfoEntry> records = new LinkedHashMap<>();
    private final Path path = DATA_DIR.resolve(FILE_NAME);

    private BchInfoManager() {
        ensureDataDir();
        load();
    }

    public static synchronized BchInfoManager getInstance() {
        if (instance == null) instance = new BchInfoManager();
        return instance;
    }

    // ========== API ==========

    /** Создать новую цепочку (после первого HEADER-блока). */
    public synchronized void addBlockchain(long blockchainId,
                                           String userLogin,
                                           byte[] publicKey32,
                                           int blockchainSizeLimit) {
        if (publicKey32 == null || publicKey32.length != 32)
            throw new IllegalArgumentException("publicKey32 must be 32 bytes");
        if (records.containsKey(blockchainId))
            throw new IllegalArgumentException("blockchain already exists: " + blockchainId);

        BchInfoEntry entry = new BchInfoEntry(
                blockchainId,
                userLogin,
                Base64.getEncoder().encodeToString(publicKey32),
                blockchainSizeLimit,
                0, 0, ""
        );

        records.put(blockchainId, entry);
        log.info("Добавлен блокчейн id={} login='{}' (лимит {})", blockchainId, userLogin, blockchainSizeLimit);
        save();
    }

    /** Обновить состояние после добавления нового блока. */
    public synchronized void updateBlockchainState(long blockchainId,
                                                   int lastBlockNumber,
                                                   String lastBlockHash,
                                                   int blockchainSize) {
        BchInfoEntry prev = getEntryOrThrow(blockchainId);

        BchInfoEntry updated = new BchInfoEntry(
                prev.blockchainId,
                prev.userLogin,
                prev.publicKeyBase64,
                prev.blockchainSizeLimit,
                blockchainSize,
                lastBlockNumber,
                lastBlockHash
        );

        records.put(blockchainId, updated);
        log.info("Обновлено состояние id={} lastNum={} hash={} size={}",
                blockchainId, lastBlockNumber, lastBlockHash, blockchainSize);
        save();
    }

    /** Получить полную информацию по blockchainId. */
    public synchronized BchInfoEntry getBchInfo(long blockchainId) {
        return records.get(blockchainId);
    }

    /** Быстро проверить существование цепочки. */
    public synchronized boolean exists(long blockchainId) {
        return records.containsKey(blockchainId);
    }

    /** id → userLogin (для поиска пользователей). */
    public synchronized Map<Long, String> getAllLoginsSnapshot() {
        Map<Long, String> copy = new LinkedHashMap<>(records.size());
        for (var e : records.entrySet()) {
            copy.put(e.getKey(), e.getValue().userLogin);
        }
        return copy;
    }

    // ========== private ==========

    private BchInfoEntry getEntryOrThrow(long blockchainId) {
        BchInfoEntry e = records.get(blockchainId);
        if (e == null) throw new IllegalStateException("Блокчейн с id=" + blockchainId + " не найден.");
        return e;
    }

    private void ensureDataDir() {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
                log.info("Создана директория данных: {}", DATA_DIR);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию хранения: " + DATA_DIR, e);
        }
    }

    private synchronized void load() {
        if (!Files.exists(path)) {
            save();
            log.info("Создан JSON-хранилище: {}", path);
            return;
        }
        try {
            byte[] json = Files.readAllBytes(path);
            if (json.length == 0) return;

            Map<String, BchInfoEntry> map = MAPPER.readValue(
                    json,
                    MAPPER.getTypeFactory().constructMapType(Map.class, String.class, BchInfoEntry.class)
            );

            records.clear();
            for (var e : map.entrySet()) {
                try {
                    long id = Long.parseLong(e.getKey());
                    records.put(id, e.getValue());
                } catch (NumberFormatException nfe) {
                    log.warn("Пропущен некорректный ключ '{}' в JSON", e.getKey());
                }
            }
            log.info("Загружено {} записей из {}", records.size(), path);
        } catch (IOException e) {
            log.error("Ошибка загрузки {}", path, e);
        }
    }

    /** Атомарная запись JSON через .tmp + ATOMIC_MOVE */
    private synchronized void save() {
        try {
            Map<String, BchInfoEntry> map = new LinkedHashMap<>();
            for (var e : records.entrySet())
                map.put(String.valueOf(e.getKey()), e.getValue());

            byte[] json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(map);

            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.write(tmp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            log.debug("Сохранено {} записей в {}", records.size(), path);
        } catch (IOException e) {
            log.error("Ошибка сохранения {}", path, e);
        }
    }
}

package utils.files;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;

/**
 * ===============================================================
 *  FileStoreUtil — синглтон-утилита для записи/дозаписи/чтения файлов.
 *  ---------------------------------------------------------------
 *  Где хранит:
 *    • Все файлы размещаются в внешней папке DATA_DIR = "data" (в корне запуска).
 *      Папка создаётся автоматически при первом обращении.
 *.
 *  Что умеет:
 *    • newFile(String fileName, byte[] data)
 *        - создаёт/переписывает файл с именем fileName и записывает data.
 *    • addDataToFile(String fileName, byte[] data)
 *        - дописывает data в конец файла (создаст файл, если его ещё нет).
 *    • readAllDataFromFile(String fileName)
 *        - читает весь файл целиком и возвращает содержимое в виде byte[].
 *.
 *  Обёртки под «блокчейны»:
 *    • newBlockchain(long blockchainId, byte[] data)
 *    • addDataToBlockchain(long blockchainId, byte[] data)
 *    • readAllDataFromBlockchain(long blockchainId)
 *        - те же операции, но имя файла формируется из blockchainId и расширения ".bch".
 *.
 *  Безопасность имён:
 *    • Внутри утилиты есть простая валидация имени файла: запрещены разделители путей,
 *      чтобы исключить выход из каталога data (path traversal).
 *.
 *  Совместимость: Java 17.
 * ===============================================================
 */
public final class FileStoreUtil {

    /** Базовая папка для хранения всех файлов (создаётся автоматически). */
    public static final String DATA_DIR_NAME = "data";
    /** Расширение файлов «блокчейнов». */
    public static final String BLOCKCHAIN_FILE_EXTENSION = ".bch";

    private static final FileStoreUtil INSTANCE = new FileStoreUtil();

    private final Path dataDirPath;

    private FileStoreUtil() {
        this.dataDirPath = Paths.get(DATA_DIR_NAME);
        ensureDataDirExists();
    }

    /** Получить единственный экземпляр утилиты. */
    public static FileStoreUtil getInstance() {
        return INSTANCE;
    }

    // ===============================================================
    //                       ОБЩИЕ МЕТОДЫ РАБОТЫ С ФАЙЛОМ
    // ===============================================================

    /**
     * Создать/переписать файл и записать в него массив байт.
     * @param fileName имя файла (без каталогов)
     * @param data     содержимое
     * @throws IllegalArgumentException при неверном имени или null-данных
     * @throws IllegalStateException при ошибках ввода/вывода
     */
    public void newFile(String fileName, byte[] data) {
        Objects.requireNonNull(data, "Данные не должны быть null");
        Path target = resolveSafe(fileName);
        try {
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось записать файл: " + target, e);
        }
    }

    /**
     * Дозаписать массив байт в конец файла (создаст файл, если отсутствует).
     * @param fileName имя файла (без каталогов)
     * @param data     добавляемые данные
     * @throws IllegalArgumentException при неверном имени или null-данных
     * @throws IllegalStateException при ошибках ввода/вывода
     */
    public void addDataToFile(String fileName, byte[] data) {
        Objects.requireNonNull(data, "Данные не должны быть null");
        Path target = resolveSafe(fileName);
        try {
            Files.write(target, data,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось дописать файл: " + target, e);
        }
    }

    /**
     * Прочитать весь файл в память и вернуть как byte[].
     * @param fileName имя файла (без каталогов)
     * @return содержимое файла
     * @throws IllegalStateException если файл не существует или ошибка ввода/вывода
     */
    public byte[] readAllDataFromFile(String fileName) {
        Path target = resolveSafe(fileName);
        if (!Files.exists(target)) {
            throw new IllegalStateException("Файл не найден: " + target);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл: " + target, e);
        }
    }

    // ===============================================================
    //                  ОБЁРТКИ ДЛЯ «БЛОКЧЕЙН-ФАЙЛОВ»
    // ===============================================================

    /**
     * Обёртка над newFile: имя формируется из blockchainId + ".bch".
     */
    public void newBlockchain(long blockchainId, byte[] data) {
        String fileName = buildBlockchainFileName(blockchainId);
        newFile(fileName, data);
    }

    /**
     * Обёртка над addDataToFile: имя формируется из blockchainId + ".bch".
     */
    public void addDataToBlockchain(long blockchainId, byte[] data) {
        String fileName = buildBlockchainFileName(blockchainId);
        addDataToFile(fileName, data);
    }

    /**
     * Обёртка над readAllDataFromFile: имя формируется из blockchainId + ".bch".
     */
    public byte[] readAllDataFromBlockchain(long blockchainId) {
        String fileName = buildBlockchainFileName(blockchainId);
        return readAllDataFromFile(fileName);
    }

    // ===============================================================
    //                           ВСПОМОГАТЕЛЬНЫЕ
    // ===============================================================

    private void ensureDataDirExists() {
        try {
            if (!Files.exists(dataDirPath)) {
                Files.createDirectories(dataDirPath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию хранения: " + dataDirPath, e);
        }
    }

    /**
     * Безопасно собрать путь внутри каталога data, запретив подстановку каталогов в имени файла.
     */
    private Path resolveSafe(String fileName) {
        validateSimpleFileName(fileName);
        return dataDirPath.resolve(fileName);
    }

    /**
     * Простейшая валидация имени файла:
     *  • запретить разделители путей и возврат на родительский каталог.
     */
    private void validateSimpleFileName(String fileName) {
        Objects.requireNonNull(fileName, "Имя файла не должно быть null");
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла не должно быть пустым");
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Недопустимое имя файла: " + fileName);
        }
    }

    /**
     * Построить имя «блокчейн-файла» из идентификатора и расширения .bch.
     * Пример:  12345  →  "12345.bch"
     */
    private String buildBlockchainFileName(long blockchainId) {
        return Long.toString(blockchainId) + BLOCKCHAIN_FILE_EXTENSION;
    }
}

package utils.files;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * ===============================================================
 *  FileStoreUtilSelfTest — запускаемый тест утилиты FileStoreUtil.
 *  ---------------------------------------------------------------
 *  Сценарий:
 *    1) Создаём «блокчейн-файл» для id=20251021 с начальными данными.
 *    2) Дозаписываем ещё порцию данных.
 *    3) Читаем целиком и печатаем длину + превью.
 *.
 *  Ожидаемый итог:
 *    • В папке "data" появится файл "20251021.bch"
 *    • В консоли будет длина содержимого и небольшой превью-дамп.
 * ===============================================================
 */
public class FileStoreUtilSelfTest {

    public static void main(String[] args) {
        System.out.println("=== FileStoreUtil self-test ===");

        FileStoreUtil fs = FileStoreUtil.getInstance();

        long blockchainId = 20251021L;

        byte[] part1 = "Hello ".getBytes(StandardCharsets.UTF_8);
        byte[] part2 = "Blockchain!".getBytes(StandardCharsets.UTF_8);

        // 1) создаём новый файл для «блокчейна»
        fs.newBlockchain(blockchainId, part1);

        // 2) дозаписываем данные
        fs.addDataToBlockchain(blockchainId, part2);

        // 3) читаем всё содержимое и показываем превью
        byte[] all = fs.readAllDataFromBlockchain(blockchainId);
        System.out.println("Total bytes read: " + all.length);
        System.out.println("Preview (UTF-8): " + new String(all, StandardCharsets.UTF_8));

        // небольшой hex-дамп первых 32 байт (для наглядности)
        System.out.println("Preview (HEX 32B): " + toHexPreview(all, 32));

        System.out.println("✅ Self-test passed (файл: data/" + blockchainId + FileStoreUtil.BLOCKCHAIN_FILE_EXTENSION + ")");
    }

    private static String toHexPreview(byte[] data, int max) {
        int n = Math.min(data.length, max);
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02X", data[i]));
            if (i + 1 < n) sb.append(' ');
        }
        if (data.length > n) sb.append(" ...");
        return sb.toString();
    }
}

package utils.search;


import utils.blockchain.BchInfoManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UserSearchService — поиск первых 5 пользователей по подстроке логина (без учёта регистра).
 */
public final class UserSearchService {

    private static final UserSearchService INSTANCE = new UserSearchService();
    private UserSearchService() {}
    public static UserSearchService getInstance() { return INSTANCE; }

    /** Результат одной пары: id + исходный login (с родным регистром). */
    public static final class Pair {
        public final long blockchainId;
        public final String userLogin;
        public Pair(long blockchainId, String userLogin) {
            this.blockchainId = blockchainId;
            this.userLogin = userLogin;
        }
    }

    /**
     * Найти первые до 5 логинов, содержащих подстроку (case-insensitive).
     */
    public List<Pair> searchFirst5(String query) {
        String q = (query == null ? "" : query).toLowerCase(Locale.ROOT).trim();
        List<Pair> out = new ArrayList<>(5);
        if (q.isEmpty()) return out;

        // берём снапшот id→login
        Map<Long, String> all = BchInfoManager.getInstance().getAllLoginsSnapshot();

        for (var e : all.entrySet()) {
            if (out.size() >= 5) break;
            String login = e.getValue() == null ? "" : e.getValue();
            if (login.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Pair(e.getKey(), login));
            }
        }
        return out;
    }

    // Упаковка пары в байтовый формат ответа: [8] id + [1] L + [L] login UTF-8 (L<=255)
    public static byte[] packPair(Pair p) {
        byte[] loginUtf8 = (p.userLogin == null ? "" : p.userLogin).getBytes(StandardCharsets.UTF_8);
        int L = Math.min(loginUtf8.length, 255);
        byte[] b = new byte[8 + 1 + L];
        // beLong
        b[0]=(byte)((p.blockchainId>>>56)&0xFF);
        b[1]=(byte)((p.blockchainId>>>48)&0xFF);
        b[2]=(byte)((p.blockchainId>>>40)&0xFF);
        b[3]=(byte)((p.blockchainId>>>32)&0xFF);
        b[4]=(byte)((p.blockchainId>>>24)&0xFF);
        b[5]=(byte)((p.blockchainId>>>16)&0xFF);
        b[6]=(byte)((p.blockchainId>>>8 )&0xFF);
        b[7]=(byte)((p.blockchainId     )&0xFF);
        b[8]=(byte)L;
        System.arraycopy(loginUtf8, 0, b, 9, L);
        return b;
    }
}