// =======================
// blockchain/body/ConnectionBody.java   (ИЗМЕНЁННЫЙ: bodyBytes без type/subType/version, + line fields)
// =======================
package blockchain.body;

import shine.db.MsgSubType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * ConnectionBody — type=3, ver=1 (в заголовке блока).
 *
 * subType (в заголовке блока) как MsgSubType:
 *   FRIEND=10, UNFRIEND=11
 *   CONTACT=20, UNCONTACT=21
 *   FOLLOW=30, UNFOLLOW=31
 *
 * bodyBytes (BigEndian), новый формат:
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *
 *   [1] toLoginLen (uint8)
 *   [N] toLogin UTF-8
 *
 *   [1] toBlockchainNameLen (uint8)
 *   [M] toBlockchainName UTF-8
 *   [4] toBlockGlobalNumber (int32)
 *   [32] toBlockHash32 (raw 32 bytes)
 */
public final class ConnectionBody implements BodyRecord, BodyHasTarget, BodyHasLine {

    public static final short TYPE = 3;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    public final short subType; // из header
    public final short version; // из header

    // line
    public final int prevLineNumber;
    public final byte[] prevLineHash32;
    public final int thisLineNumber;

    // payload
    public final String toLogin;
    public final String toBlockchainName;
    public final int toBlockGlobalNumber;
    public final byte[] toBlockHash32;

    public ConnectionBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("ConnectionBody version must be 1, got=" + (this.version & 0xFFFF));
        }
        if (!isValidSubType(this.subType)) {
            throw new IllegalArgumentException("Bad connection subType: " + (this.subType & 0xFFFF));
        }

        // минимум:
        // line(4+32+4) + toLoginLen[1]+toLogin[1] + toBchLen[1]+toBch[1] + global[4] + hash[32]
        if (bodyBytes.length < (4 + 32 + 4) + 1 + 1 + 1 + 1 + 4 + 32) {
            throw new IllegalArgumentException("ConnectionBody too short");
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        this.prevLineNumber = bb.getInt();

        this.prevLineHash32 = new byte[32];
        bb.get(this.prevLineHash32);

        this.thisLineNumber = bb.getInt();

        int toLoginLen = Byte.toUnsignedInt(bb.get());
        if (toLoginLen <= 0) throw new IllegalArgumentException("toLoginLen is 0");
        if (bb.remaining() < toLoginLen) throw new IllegalArgumentException("toLogin payload too short");

        byte[] toLoginBytes = new byte[toLoginLen];
        bb.get(toLoginBytes);
        this.toLogin = new String(toLoginBytes, StandardCharsets.UTF_8);

        int bchLen = Byte.toUnsignedInt(bb.get());
        if (bchLen <= 0) throw new IllegalArgumentException("toBlockchainNameLen is 0");
        if (bb.remaining() < bchLen + 4 + 32) throw new IllegalArgumentException("Connection payload too short");

        byte[] bchBytes = new byte[bchLen];
        bb.get(bchBytes);
        this.toBlockchainName = new String(bchBytes, StandardCharsets.UTF_8);

        this.toBlockGlobalNumber = bb.getInt();

        this.toBlockHash32 = new byte[32];
        bb.get(this.toBlockHash32);

        if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
    }

    public ConnectionBody(int prevLineNumber,
                          byte[] prevLineHash32,
                          int thisLineNumber,
                          short subType,
                          String toLogin,
                          String toBlockchainName,
                          int toBlockGlobalNumber,
                          byte[] toBlockHash32) {

        Objects.requireNonNull(toLogin, "toLogin == null");
        Objects.requireNonNull(toBlockchainName, "toBlockchainName == null");
        Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");

        if (!isValidSubType(subType)) throw new IllegalArgumentException("Bad connection subType: " + (subType & 0xFFFF));
        if (toLogin.isBlank()) throw new IllegalArgumentException("toLogin is blank");
        if (!toLogin.matches("^[A-Za-z0-9_]+$")) throw new IllegalArgumentException("toLogin must match ^[A-Za-z0-9_]+$");

        if (toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
        if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

        this.prevLineNumber = prevLineNumber;
        this.prevLineHash32 = (prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        this.thisLineNumber = thisLineNumber;

        this.subType = subType;
        this.version = VER;

        this.toLogin = toLogin;
        this.toBlockchainName = toBlockchainName;
        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);
    }

    private static boolean isValidSubType(short st) {
        int v = st & 0xFFFF;
        return v == (MsgSubType.CONNECTION_FRIEND & 0xFFFF)
                || v == (MsgSubType.CONNECTION_UNFRIEND & 0xFFFF)
                || v == (MsgSubType.CONNECTION_CONTACT & 0xFFFF)
                || v == (MsgSubType.CONNECTION_UNCONTACT & 0xFFFF)
                || v == (MsgSubType.CONNECTION_FOLLOW & 0xFFFF)
                || v == (MsgSubType.CONNECTION_UNFOLLOW & 0xFFFF);
    }

    @Override
    public ConnectionBody check() {
        if (!isValidSubType(subType)) throw new IllegalArgumentException("Bad connection subType: " + (subType & 0xFFFF));

        // line rule
        if (prevLineNumber == -1) {
            if (!isAllZero32(prevLineHash32)) throw new IllegalArgumentException("prevLineHash32 must be zero when prevLineNumber=-1");
            if (thisLineNumber != -1) throw new IllegalArgumentException("thisLineNumber must be -1 when prevLineNumber=-1");
        } else {
            if (prevLineHash32 == null || prevLineHash32.length != 32) throw new IllegalArgumentException("prevLineHash32 invalid");
        }

        if (toLogin == null || toLogin.isBlank()) throw new IllegalArgumentException("toLogin is blank");
        if (!toLogin.matches("^[A-Za-z0-9_]+$")) throw new IllegalArgumentException("toLogin must match ^[A-Za-z0-9_]+$");

        if (toBlockchainName == null || toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
        if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32 == null || toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 invalid");

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

        if (toBlockHash32 == null || toBlockHash32.length != 32)
            throw new IllegalArgumentException("toBlockHash32 != 32");

        int cap = (4 + 32 + 4)
                + 1 + toLoginBytes.length
                + 1 + bchBytes.length
                + 4 + 32;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putInt(prevLineNumber);
        bb.put(prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        bb.putInt(thisLineNumber);

        bb.put((byte) toLoginBytes.length);
        bb.put(toLoginBytes);

        bb.put((byte) bchBytes.length);
        bb.put(bchBytes);

        bb.putInt(toBlockGlobalNumber);
        bb.put(toBlockHash32);

        return bb.array();
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

    /* ====================== BodyHasTarget ===================== */
    @Override public String toLogin() { return toLogin; }
    @Override public String toBchName() { return toBlockchainName; }
    @Override public Integer toBlockGlobalNumber() { return toBlockGlobalNumber; }
    @Override public byte[] toBlockHasheBytes() { return toBlockHash32; }
}