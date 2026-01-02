package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * ConnectionBody — type=3, ver=1. (Связь/отношение)
 *
 * Идея:
 *  - Это запись "у меня есть связь с X".
 *  - subType определяет вид связи:
 *      10 = FRIEND   (друг)
 *      20 = CONTACT  (контакт)
 *      30 = FOLLOW   (подписан на кого-то)
 *
 * Формат bodyBytes (BigEndian):
 *   [2] type=3
 *   [2] ver=1
 *
 *   [2] subType (uint16) — вид связи (10/20/30)
 *
 *   [1] toLoginLen (uint8)
 *   [N] toLogin UTF-8
 *       ВАЖНО: toLogin — это "с кем связь" (ключевой смысл этой записи).
 *
 *   [1] toBlockchainNameLen (uint8)
 *   [M] toBlockchainName UTF-8
 *   [4] toBlockGlobalNumber (int32)
 *   [32] toBlockHash32 (raw 32 bytes)
 *
 *   ВАЖНО: поля toBlockchainName/toBlockGlobalNumber/toBlockHash32 — это
 *   "последний известный блок" того человека (снимок/якорь состояния).
 *   По сути можно было бы обойтись без них, но они полезны:
 *    - фиксируют, какой блок и какой хэш ты считаешь последним известным у друга/контакта;
 *    - помогают синхронизации/проверкам (например, если потом сравнивать, насколько данные устарели).
 *
 * ЛИНИЯ:
 *  - строго lineIndex=3 (выделяем отдельную линию под связи).
 */
public final class ConnectionBody implements BodyRecord {

    public static final short TYPE = 3;
    public static final short VER  = 1;

    /** Удобный ключ для BodyRecordParser: (type<<16)|ver */
    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    // subType:
    public static final short SUB_FRIEND  = 10;
    public static final short SUB_CONTACT = 20;
    public static final short SUB_FOLLOW  = 30;

    public final short subType;

    /** С кем связь (главное поле). */
    public final String toLogin;

    /** Блокчейн того человека (снимок/якорь). */
    public final String toBlockchainName;

    /** Номер последнего известного блока у того человека (снимок/якорь). */
    public final int toBlockGlobalNumber;

    /** Хэш последнего известного блока у того человека (снимок/якорь). */
    public final byte[] toBlockHash32;

    /* ===================================================================== */
    /* ====================== Конструктор из байт =========================== */
    /* ===================================================================== */

    public ConnectionBody(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        // минимум:
        // type[2]+ver[2]+subType[2] +
        // toLoginLen[1]+toLogin[1] +
        // toBchLen[1]+toBch[1] +
        // global[4] + hash[32]
        if (bodyBytes.length < 2 + 2 + 2 + 1 + 1 + 1 + 1 + 4 + 32) {
            throw new IllegalArgumentException("ConnectionBody too short");
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER) {
            throw new IllegalArgumentException("Not ConnectionBody: type=" + type + " ver=" + ver);
        }

        this.subType = bb.getShort();
        if (!isValidSubType(this.subType)) {
            throw new IllegalArgumentException("Bad connection subType: " + (this.subType & 0xFFFF));
        }

        // --- toLogin ---
        int toLoginLen = Byte.toUnsignedInt(bb.get());
        if (toLoginLen <= 0) throw new IllegalArgumentException("toLoginLen is 0");
        if (bb.remaining() < toLoginLen) throw new IllegalArgumentException("toLogin payload too short");

        byte[] toLoginBytes = new byte[toLoginLen];
        bb.get(toLoginBytes);
        this.toLogin = new String(toLoginBytes, StandardCharsets.UTF_8);

        // --- toBlockchainName + snapshot блока ---
        if (bb.remaining() < 1) throw new IllegalArgumentException("Missing toBlockchainNameLen");
        int bchLen = Byte.toUnsignedInt(bb.get());
        if (bchLen <= 0) throw new IllegalArgumentException("toBlockchainNameLen is 0");
        if (bb.remaining() < bchLen + 4 + 32) throw new IllegalArgumentException("Connection payload too short");

        byte[] bchBytes = new byte[bchLen];
        bb.get(bchBytes);
        this.toBlockchainName = new String(bchBytes, StandardCharsets.UTF_8);

        this.toBlockGlobalNumber = bb.getInt();

        this.toBlockHash32 = new byte[32];
        bb.get(this.toBlockHash32);

        // запрет мусора в конце
        if (bb.remaining() != 0) {
            throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
        }
    }

    /* ===================================================================== */
    /* ====================== Конструктор “вручную” ========================= */
    /* ===================================================================== */

    public ConnectionBody(short subType,
                          String toLogin,
                          String toBlockchainName,
                          int toBlockGlobalNumber,
                          byte[] toBlockHash32) {

        Objects.requireNonNull(toLogin, "toLogin == null");
        Objects.requireNonNull(toBlockchainName, "toBlockchainName == null");
        Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");

        if (!isValidSubType(subType)) {
            throw new IllegalArgumentException("Unknown connection subType: " + (subType & 0xFFFF));
        }

        if (toLogin.isBlank()) throw new IllegalArgumentException("toLogin is blank");
        if (!toLogin.matches("^[A-Za-z0-9_]+$"))
            throw new IllegalArgumentException("toLogin must match ^[A-Za-z0-9_]+$");

        if (toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
        if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

        this.subType = subType;
        this.toLogin = toLogin;
        this.toBlockchainName = toBlockchainName;
        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);
    }

    private static boolean isValidSubType(short st) {
        return st == SUB_FRIEND || st == SUB_CONTACT || st == SUB_FOLLOW;
    }

    /* ===================================================================== */
    /* ====================== BodyRecord контракт =========================== */
    /* ===================================================================== */

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }
    @Override public short subType() { return subType; }

    @Override
    public short expectedLineIndex() {
        return 3;
    }

    @Override
    public ConnectionBody check() {
        if (!isValidSubType(subType))
            throw new IllegalArgumentException("Bad connection subType: " + (subType & 0xFFFF));

        if (toLogin == null || toLogin.isBlank())
            throw new IllegalArgumentException("toLogin is blank");
        if (!toLogin.matches("^[A-Za-z0-9_]+$"))
            throw new IllegalArgumentException("toLogin must match ^[A-Za-z0-9_]+$");

        if (toBlockchainName == null || toBlockchainName.isBlank())
            throw new IllegalArgumentException("toBlockchainName is blank");
        if (toBlockGlobalNumber < 0)
            throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32 == null || toBlockHash32.length != 32)
            throw new IllegalArgumentException("toBlockHash32 invalid");

        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] toLoginBytes = toLogin.getBytes(StandardCharsets.UTF_8);
        if (toLoginBytes.length == 0 || toLoginBytes.length > 255)
            throw new IllegalArgumentException("toLogin utf8 len must be 1..255");

        byte[] bchBytes = toBlockchainName.getBytes(StandardCharsets.UTF_8);
        if (bchBytes.length == 0 || bchBytes.length > 255)
            throw new IllegalArgumentException("toBlockchainName utf8 len must be 1..255");

        if (!isValidSubType(subType))
            throw new IllegalArgumentException("Bad connection subType: " + (subType & 0xFFFF));
        if (toBlockHash32 == null || toBlockHash32.length != 32)
            throw new IllegalArgumentException("toBlockHash32 != 32");

        // type[2]+ver[2]+subType[2]
        // + toLoginLen[1]+toLogin[N]
        // + toBchLen[1]+toBch[M]
        // + global[4]+hash[32]
        int cap = 2 + 2 + 2
                + 1 + toLoginBytes.length
                + 1 + bchBytes.length
                + 4 + 32;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putShort(TYPE);
        bb.putShort(VER);

        bb.putShort(subType);

        bb.put((byte) toLoginBytes.length);
        bb.put(toLoginBytes);

        bb.put((byte) bchBytes.length);
        bb.put(bchBytes);

        bb.putInt(toBlockGlobalNumber);
        bb.put(toBlockHash32);

        return bb.array();
    }

    @Override
    public String toString() {
        String st = switch (subType) {
            case SUB_FRIEND -> "FRIEND (10)";
            case SUB_CONTACT -> "CONTACT (20)";
            case SUB_FOLLOW -> "FOLLOW (30)";
            default -> "UNKNOWN";
        };

        return """
                ConnectionBody {
                  тип записи              : CONNECTION (type=3, ver=1)
                  ожидаемая линия         : 3
                  subType                 : %s
                  связь с login           : "%s"
                  блокчейн друга/цели      : "%s"
                  lastKnown globalNumber  : %d
                  lastKnown hash (hex)    : %s
                }
                """.formatted(
                st,
                toLogin,
                toBlockchainName,
                toBlockGlobalNumber,
                toBlockHashHex()
        );
    }

    public String toBlockHashHex() {
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[64];
        for (int i = 0; i < 32; i++) {
            int v = toBlockHash32[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}