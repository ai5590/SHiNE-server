package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * ============================================================================
 * HeaderBody — тело записи типа 0 (заглавие блокчейна)
 * ============================================================================
 *.
 * 🧩 Назначение:
 *   Первый блок каждой пользовательской цепочки (.bch) — это "заголовок".
 *   Он хранит базовую информацию о владельце, версии и публичном ключе.
 *.
 *   Этот блок всегда имеет:
 *     • recordType = 0
 *     • recordNumber = 0
 *     • recordTypeVersion = 1
 *.
 * ----------------------------------------------------------------------------
 * 🔹 Формат body (без общих 20 байт заголовка блока BchBlock)
 *.
 * | Смещение | Размер | Поле                | Формат | Описание |
 * |-----------|--------|--------------------|---------|-----------|
 * | 0x00      | 8      | tag                | ASCII   | Статическая сигнатура "SHiNE001" |
 * | 0x08      | 8      | blockchainId       | long BE | Уникальный идентификатор цепочки |
 * | 0x10      | 1      | userLoginLength=N  | uint8   | Длина логина пользователя |
 * | 0x11      | N      | userLogin          | UTF-8   | Логин пользователя |
 * | 0x11+N    | 4      | blockchainType     | int BE  | Зарезервировано (всегда 0) |
 * | 0x15+N    | 4      | blockchainNumber   | int BE  | Зарезервировано (всегда 0) |
 * | 0x19+N    | 2      | versionUserBch     | short BE| Версия формата (всегда 1) |
 * | 0x1B+N    | 8      | prevUserBchId      | long BE | Зарезервировано (всегда 0) |
 * | 0x23+N    | 32     | publicKey32        | raw     | Публичный ключ (Ed25519, 32 байта) |
 *.
 * ----------------------------------------------------------------------------
 * 💡 Пример структуры в байтах:
 *.
 * 0000: 53 48 69 4E 45 30 30 31     "SHiNE001"
 * 0008: 00 00 00 00 01 23 45 67     blockchainId
 * 0010: 05                           userLoginLength = 5
 * 0011: 41 69 64 61 72              userLogin = "Aidar"
 * 0016: 00 00 00 00                 blockchainType = 0
 * 001A: 00 00 00 00                 blockchainNumber = 0
 * 001E: 00 01                       versionUserBch = 1
 * 0020: 00 00 00 00 00 00 00 00     prevUserBchId = 0
 * 0028: [32 байта публичного ключа]
 *.
 * ----------------------------------------------------------------------------
 * 📘 Замечания:
 *   • Поля blockchainType, blockchainNumber, versionUserBch, prevUserBchId
 *     зарезервированы для будущего расширения формата.
 *   • На данный момент все они принимают фиксированные значения:
 *       blockchainType = 0
 *       blockchainNumber = 0
 *       versionUserBch = 1
 *       prevUserBchId = 0
 *.
 * ============================================================================
 */
public final class HeaderBody implements BodyRecord {

    public static final short TYPE = 0;
    public static final String TAG = "SHiNE001";
    public static final int PUBKEY_LEN = 32;

    public final String tag; // всегда "SHiNE001"
    public final long blockchainId;
    public final String userLogin;     // UTF-8
    public final int blockchainType;   // пока 0
    public final int blockchainNumber; // пока 0
    public final short versionUserBch; // пока 1
    public final long prevUserBchId;   // пока 0
    public final byte[] publicKey32;   // 32 байта

    // ------------------------------------------------------------
    // Конструктор №1 — из массива байт (для парсинга существующего блока)
    // ------------------------------------------------------------
    public HeaderBody(byte[] body) {
        Objects.requireNonNull(body, "body == null");
        if (body.length < 8 + 8 + 1 + 2 + 4 + 4 + 8 + 32)
            throw new IllegalArgumentException("HeaderBody слишком короткое");

        ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN);

        // [8] тег
        byte[] tagBytes = new byte[8];
        buf.get(tagBytes);
        String tag = new String(tagBytes, StandardCharsets.US_ASCII);
        if (!TAG.equals(tag))
            throw new IllegalArgumentException("Неверный тег: " + tag);
        this.tag = tag;

        // [8] blockchainId
        this.blockchainId = buf.getLong();

        // [1] длина логина
        int loginLen = Byte.toUnsignedInt(buf.get());
        if (loginLen == 0 || buf.remaining() < loginLen + 4 + 4 + 2 + 8 + 32)
            throw new IllegalArgumentException("Некорректная длина логина");

        // [N] логин
        byte[] loginBytes = new byte[loginLen];
        buf.get(loginBytes);
        this.userLogin = new String(loginBytes, StandardCharsets.UTF_8);

        // Остальные поля
        this.blockchainType = buf.getInt();
        this.blockchainNumber = buf.getInt();
        this.versionUserBch = buf.getShort();
        this.prevUserBchId = buf.getLong();

        this.publicKey32 = new byte[PUBKEY_LEN];
        buf.get(this.publicKey32);
    }

    // ------------------------------------------------------------
    // Конструктор №2 — из параметров (для создания нового заголовка)
    // ------------------------------------------------------------
    public HeaderBody(long blockchainId, String userLogin,
                      int blockchainType, int blockchainNumber,
                      short versionUserBch, long prevUserBchId,
                      byte[] publicKey32) {

        Objects.requireNonNull(userLogin, "userLogin == null");
        Objects.requireNonNull(publicKey32, "publicKey32 == null");

        if (publicKey32.length != PUBKEY_LEN)
            throw new IllegalArgumentException("Публичный ключ должен состоять из 32 байт");

        this.tag = TAG;
        this.blockchainId = blockchainId;
        this.userLogin = userLogin;
        this.blockchainType = blockchainType;
        this.blockchainNumber = blockchainNumber;
        this.versionUserBch = versionUserBch;
        this.prevUserBchId = prevUserBchId;
        this.publicKey32 = Arrays.copyOf(publicKey32, PUBKEY_LEN);
    }

    // ------------------------------------------------------------
    // Проверка и сериализация
    // ------------------------------------------------------------
    @Override
    public HeaderBody check() {
        if (userLogin == null || userLogin.isBlank())
            throw new IllegalArgumentException("Логин не может быть пустым");
        if (!userLogin.matches("^[A-Za-z0-9_]+$"))
            throw new IllegalArgumentException("Логин может содержать только латиницу, цифры и _");
        if (publicKey32 == null || publicKey32.length != PUBKEY_LEN)
            throw new IllegalArgumentException("Публичный ключ должен быть 32 байта");
        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] loginUtf8 = userLogin.getBytes(StandardCharsets.UTF_8);
        if (loginUtf8.length > 255)
            throw new IllegalArgumentException("Логин слишком длинный (>255 байт)");

        int cap = 8 + 8 + 1 + loginUtf8.length + 4 + 4 + 2 + 8 + 32;
        ByteBuffer buf = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        buf.put(TAG.getBytes(StandardCharsets.US_ASCII)); // [8]
        buf.putLong(blockchainId);                        // [8]
        buf.put((byte) loginUtf8.length);                 // [1]
        buf.put(loginUtf8);                               // [N]
        buf.putInt(blockchainType);                       // [4]
        buf.putInt(blockchainNumber);                     // [4]
        buf.putShort(versionUserBch);                     // [2]
        buf.putLong(prevUserBchId);                       // [8]
        buf.put(publicKey32);                             // [32]

        return buf.array();
    }

    @Override
    public String toString() {
        return "HeaderBody{" +
                "id=" + blockchainId +
                ", login='" + userLogin + '\'' +
                ", type=" + blockchainType +
                ", num=" + blockchainNumber +
                ", ver=" + versionUserBch +
                ", prev=" + prevUserBchId +
                ", pubkey32=" + Arrays.toString(Arrays.copyOf(publicKey32, 4)) + "..." +
                '}';
    }
}
