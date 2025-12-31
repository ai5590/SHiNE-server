package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * HeaderBody — type=0, version=1.
 *
 * Полный bodyBytes:
 *   [2] type=0
 *   [2] version=1
 *   [8] tag ASCII "SHiNE"
 *   [1] loginLength=N (uint8)
 *   [N] login UTF-8
 *
 * ЛИНИЯ:
 *  - строго lineIndex=0 (genesis)
 */
public final class HeaderBody implements BodyRecord {

    public static final short TYPE = 0;
    public static final short VER  = 1;

    public static final String TAG = "SHiNE";

    public final String tag;   // "SHiNE"
    public final String login;

    /** Десериализация из полного bodyBytes (включая type/version). */
    public HeaderBody(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        if (bodyBytes.length < 4) throw new IllegalArgumentException("HeaderBody too short (<4)");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);
        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER)
            throw new IllegalArgumentException("Not HeaderBody: type=" + type + " ver=" + ver);

        if (bb.remaining() < 8 + 1)
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
    }

    /** Создание “вручную” (для генерации первого блока). */
    public HeaderBody(String login) {
        Objects.requireNonNull(login, "login == null");
        this.tag = TAG;
        this.login = login;
    }

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }

    @Override
    public short expectedLineIndex() {
        return 0;
    }

    @Override
    public HeaderBody check() {
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

        int cap = 4 + 8 + 1 + loginUtf8.length;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putShort(TYPE);
        bb.putShort(VER);

        bb.put(TAG.getBytes(StandardCharsets.US_ASCII)); // [8]
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
                  тег формата       : "%s"
                  login владельца   : "%s"
                }
                """.formatted(tag, login);
    }
}