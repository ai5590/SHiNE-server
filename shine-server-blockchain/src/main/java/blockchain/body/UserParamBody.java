// =======================
// blockchain/body/UserParamBody.java   (ИЗМЕНЁННЫЙ: bodyBytes без type/subType/version, + line fields)
// =======================
package blockchain.body;

import shine.db.MsgSubType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * UserParamBody — type=4, ver=1 (в заголовке блока).
 *
 * subType (в заголовке блока):
 *   1 = TEXT_TEXT
 *
 * bodyBytes (BigEndian), новый формат:
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *
 *   [2] keyLenBytes   (uint16)
 *   [N] keyUtf8
 *
 *   [2] valueLenBytes (uint16)
 *   [M] valueUtf8
 */
public final class UserParamBody implements BodyRecord, BodyHasLine {

    public static final short TYPE = 4;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    public final short subType; // из header
    public final short version; // из header

    // line
    public final int prevLineNumber;
    public final byte[] prevLineHash32;
    public final int thisLineNumber;

    public final String paramKey;
    public final String paramValue;

    public UserParamBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("UserParamBody version must be 1, got=" + (this.version & 0xFFFF));
        }
        if ((this.subType & 0xFFFF) != (MsgSubType.USER_PARAM_TEXT_TEXT & 0xFFFF)) {
            throw new IllegalArgumentException("Bad UserParam subType: " + (this.subType & 0xFFFF));
        }

        // минимум: line(4+32+4) + keyLen(2)+key(1) + valLen(2)+val(1)
        if (bodyBytes.length < (4 + 32 + 4) + 2 + 1 + 2 + 1) {
            throw new IllegalArgumentException("UserParamBody too short");
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        this.prevLineNumber = bb.getInt();

        this.prevLineHash32 = new byte[32];
        bb.get(this.prevLineHash32);

        this.thisLineNumber = bb.getInt();

        int keyLen = Short.toUnsignedInt(bb.getShort());
        if (keyLen <= 0) throw new IllegalArgumentException("paramKeyLen is 0");
        if (bb.remaining() < keyLen + 2) throw new IllegalArgumentException("UserParam key payload too short");

        byte[] keyBytes = new byte[keyLen];
        bb.get(keyBytes);

        int valLen = Short.toUnsignedInt(bb.getShort());
        if (valLen <= 0) throw new IllegalArgumentException("paramValueLen is 0");
        if (bb.remaining() < valLen) throw new IllegalArgumentException("UserParam value payload too short");

        byte[] valBytes = new byte[valLen];
        bb.get(valBytes);

        if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());

        this.paramKey = strictUtf8(keyBytes, "paramKey");
        this.paramValue = strictUtf8(valBytes, "paramValue");

        if (this.paramKey.isBlank()) throw new IllegalArgumentException("paramKey is blank");
        if (this.paramValue.isBlank()) throw new IllegalArgumentException("paramValue is blank");
    }

    public UserParamBody(int prevLineNumber,
                         byte[] prevLineHash32,
                         int thisLineNumber,
                         String paramKey,
                         String paramValue) {

        Objects.requireNonNull(paramKey, "paramKey == null");
        Objects.requireNonNull(paramValue, "paramValue == null");

        this.subType = MsgSubType.USER_PARAM_TEXT_TEXT;
        this.version = VER;

        this.prevLineNumber = prevLineNumber;
        this.prevLineHash32 = (prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        this.thisLineNumber = thisLineNumber;

        if (paramKey.isBlank()) throw new IllegalArgumentException("paramKey is blank");
        if (paramValue.isBlank()) throw new IllegalArgumentException("paramValue is blank");

        this.paramKey = paramKey;
        this.paramValue = paramValue;
    }

    @Override
    public UserParamBody check() {
        if ((subType & 0xFFFF) != (MsgSubType.USER_PARAM_TEXT_TEXT & 0xFFFF))
            throw new IllegalArgumentException("Bad UserParam subType: " + (subType & 0xFFFF));

        if (prevLineNumber == -1) {
            if (!isAllZero32(prevLineHash32)) throw new IllegalArgumentException("prevLineHash32 must be zero when prevLineNumber=-1");
            if (thisLineNumber != -1) throw new IllegalArgumentException("thisLineNumber must be -1 when prevLineNumber=-1");
        } else {
            if (prevLineHash32 == null || prevLineHash32.length != 32) throw new IllegalArgumentException("prevLineHash32 invalid");
        }

        if (paramKey == null || paramKey.isBlank()) throw new IllegalArgumentException("paramKey is blank");
        if (paramValue == null || paramValue.isBlank()) throw new IllegalArgumentException("paramValue is blank");

        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] keyUtf8 = paramKey.getBytes(StandardCharsets.UTF_8);
        byte[] valUtf8 = paramValue.getBytes(StandardCharsets.UTF_8);

        if (keyUtf8.length == 0 || keyUtf8.length > 65535) throw new IllegalArgumentException("paramKey utf8 len must be 1..65535");
        if (valUtf8.length == 0 || valUtf8.length > 65535) throw new IllegalArgumentException("paramValue utf8 len must be 1..65535");

        int cap = (4 + 32 + 4)
                + 2 + keyUtf8.length
                + 2 + valUtf8.length;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putInt(prevLineNumber);
        bb.put(prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        bb.putInt(thisLineNumber);

        bb.putShort((short) keyUtf8.length);
        bb.put(keyUtf8);

        bb.putShort((short) valUtf8.length);
        bb.put(valUtf8);

        return bb.array();
    }

    private static String strictUtf8(byte[] bytes, String fieldName) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(fieldName + " is not valid UTF-8", e);
        }
    }

    private static boolean isAllZero32(byte[] b) {
        if (b == null || b.length != 32) return true;
        for (int i = 0; i < 32; i++) if (b[i] != 0) return false;
        return true;
    }

    /* ====================== BodyHasLine ====================== */
    @Override public int prevLineNumber() { return prevLineNumber; }
    @Override public byte[] prevLineHash32() { return prevLineHash32 == null ? null : Arrays.copyOf(prevLineHash32, 32); }
    @Override public int thisLineNumber() { return thisLineNumber; }
}