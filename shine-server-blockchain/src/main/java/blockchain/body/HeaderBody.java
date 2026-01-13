// =======================
// blockchain/body/HeaderBody.java   (ИЗМЕНЁННЫЙ: bodyBytes без type/subType/version)
// =======================
package blockchain.body;

import utils.config.ShineSignatureConstants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * HeaderBody — type=0, version=1.
 *
 * В новом формате type/subType/version живут в HEADER блока,
 * поэтому bodyBytes для HeaderBody содержат только payload:
 *
 * bodyBytes (BigEndian):
 *   [TAG_LEN] tag ASCII "SHiNE"
 *   [1] loginLength=N (uint8)
 *   [N] login UTF-8
 */
public final class HeaderBody implements BodyRecord {

    public static final short TYPE = 0;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    /** Для header subType всегда 0 (служебная совместимость). */
    public static final short SUBTYPE_COMPAT = 0;

    /** TAG формата (ASCII). */
    public static final String TAG = ShineSignatureConstants.BLOCKCHAIN_HEADER_TAG;

    private static final byte[] TAG_ASCII = TAG.getBytes(StandardCharsets.US_ASCII);
    private static final int TAG_LEN = TAG_ASCII.length;

    public final short subType; // всегда 0 (из заголовка блока)
    public final short version; // из заголовка блока
    public final String tag;    // "SHiNE"
    public final String login;

    /** Десериализация из payload bodyBytes (без type/subType/version). */
    public HeaderBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.subType & 0xFFFF) != (SUBTYPE_COMPAT & 0xFFFF)) {
            throw new IllegalArgumentException("HeaderBody subType must be 0, got=" + (this.subType & 0xFFFF));
        }
        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("HeaderBody version must be 1, got=" + (this.version & 0xFFFF));
        }

        // минимум: tag[TAG_LEN] + loginLen[1]
        if (bodyBytes.length < TAG_LEN + 1) throw new IllegalArgumentException("HeaderBody too short");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        byte[] tagBytes = new byte[TAG_LEN];
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

        if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
    }

    /** Создание “вручную”. */
    public HeaderBody(String login) {
        Objects.requireNonNull(login, "login == null");
        this.subType = SUBTYPE_COMPAT;
        this.version = VER;
        this.tag = TAG;
        this.login = login;
    }

    @Override
    public HeaderBody check() {
        if ((subType & 0xFFFF) != (SUBTYPE_COMPAT & 0xFFFF))
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
        if (loginUtf8.length == 0 || loginUtf8.length > 255)
            throw new IllegalArgumentException("Login utf8 len must be 1..255");

        int cap = TAG_LEN + 1 + loginUtf8.length;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
        bb.put(TAG_ASCII);
        bb.put((byte) loginUtf8.length);
        bb.put(loginUtf8);

        return bb.array();
    }

    @Override
    public String toString() {
        return """
                HeaderBody {
                  тип записи        : HEADER (type=0, ver=1)  [в заголовке блока]
                  subType           : 0 (compat)
                  тег формата       : "%s"
                  login владельца   : "%s"
                }
                """.formatted(tag, login);
    }
}