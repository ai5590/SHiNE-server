package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * HeaderBody — type=0, version=1.
 *
 * Полный bodyBytes (BigEndian):
 *   [2] type=0
 *   [2] version=1
 *
 *   [2] subType (uint16) = 0
 *       (служебное поле для совместимости с единым форматом body,
 *        чтобы ВСЕ body имели subType одинаковым способом)
 *
 *   [5] tag ASCII "SHiNE"
 *   [1] loginLength=N (uint8)
 *   [N] login UTF-8
 *
 * ЛИНИЯ:
 *  - строго lineIndex=0 (genesis)
 */
public final class HeaderBody implements BodyRecord {

    public static final short TYPE = 0;
    public static final short VER  = 1;

    /** Для header всегда 0 (служебная совместимость). */
    public static final short SUBTYPE_COMPAT = 0;

    public static final String TAG = "SHiNE";

    public final short subType; // всегда 0
    public final String tag;    // "SHiNE"
    public final String login;

    /** Десериализация из полного bodyBytes (включая type/version/subType). */
    public HeaderBody(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        if (bodyBytes.length < 4 + 2) throw new IllegalArgumentException("HeaderBody too short (<6)");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER)
            throw new IllegalArgumentException("Not HeaderBody: type=" + type + " ver=" + ver);

        this.subType = bb.getShort();
        if (this.subType != SUBTYPE_COMPAT)
            throw new IllegalArgumentException("HeaderBody subType must be 0, got=" + (this.subType & 0xFFFF));

        // дальше: tag[5] + loginLen[1] минимум
        if (bb.remaining() < 5 + 1)
            throw new IllegalArgumentException("Header payload too short");

        byte[] tagBytes = new byte[5];
        bb.get(tagBytes);
        String t = new String(tagBytes, StandardCharsets.US_ASCII);
        if (!TAG.equals(t)) throw new IllegalArgumentException("Bad tag: " + t);
        this.tag = t;

        int loginLen = Byte.toUnsignedInt(bb.get());
        if (loginLen <= 0 || bb.remaining() < loginLen)
            throw new IllegalArgumentException("Bad login length");

        byte[] loginBytes = new byte[loginLen];
        bb.get(loginBytes);
        this.login = new String(loginBytes, StandardCharsets.UTF_8);

        // запрещаем мусор в конце
        if (bb.remaining() != 0) {
            throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
        }
    }

    /** Создание “вручную” (для генерации первого блока). */
    public HeaderBody(String login) {
        Objects.requireNonNull(login, "login == null");
        this.subType = SUBTYPE_COMPAT;
        this.tag = TAG;
        this.login = login;
    }

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }
    @Override public short subType() { return subType; }

    @Override
    public short expectedLineIndex() {
        return 0;
    }

    @Override
    public HeaderBody check() {
        if (subType != SUBTYPE_COMPAT)
            throw new IllegalArgumentException("HeaderBody subType must be 0");

        if (login == null || login.isBlank())
            throw new IllegalArgumentException("Login is blank");
        if (!login.matches("^[A-Za-z0-9_]+$"))
            throw new IllegalArgumentException("Login must match ^[A-Za-z0-9_]+$");
        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] loginUtf8 = login.getBytes(StandardCharsets.UTF_8);
        if (loginUtf8.length > 255)
            throw new IllegalArgumentException("Login too long (>255 bytes)");

        // type[2] + ver[2] + subType[2] + tag[5] + loginLen[1] + login[N]
        int cap = 2 + 2 + 2 + 5 + 1 + loginUtf8.length;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putShort(TYPE);
        bb.putShort(VER);

        bb.putShort(SUBTYPE_COMPAT);

        bb.put(TAG.getBytes(StandardCharsets.US_ASCII)); // [5]
        bb.put((byte) loginUtf8.length);                 // [1]
        bb.put(loginUtf8);                               // [N]

        return bb.array();
    }

    @Override
    public String toString() {
        return """
                HeaderBody {
                  тип записи        : HEADER (type=0, ver=1)
                  ожидаемая линия   : 0 (genesis)
                  subType           : 0 (compat)
                  тег формата       : "%s"
                  login владельца   : "%s"
                }
                """.formatted(tag, login);
    }
}