// =======================
// blockchain/body/TextBody.java   (ИЗМЕНЁННЫЙ: header содержит type/subType/version, body содержит line fields)
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
 * TextBody — type=1, ver=1 (в заголовке блока).
 *
 * subType (в заголовке блока):
 *   1  = NEW
 *   2  = REPLY
 *   3  = REPOST
 *   10 = EDIT
 *
 * bodyBytes (BigEndian), новый формат:
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *
 *   [2] textLenBytes (uint16)
 *   [N] text UTF-8
 *
 *   Далее ТОЛЬКО если subType == REPLY/REPOST/EDIT:
 *     [1] toBlockchainNameLen (uint8)
 *     [N] toBlockchainName UTF-8
 *     [4] toBlockGlobalNumber (int32)
 *     [32] toBlockHash32 (raw 32 bytes)
 */
public final class TextBody implements BodyRecord, BodyHasTarget, BodyHasLine {

    public static final short TYPE = 1;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    public final short subType;   // из header
    public final short version;   // из header

    // линейные поля
    public final int prevLineNumber;
    public final byte[] prevLineHash32; // 32
    public final int thisLineNumber;

    // payload
    public final String message;

    // target (только для reply/repost/edit)
    public final String toBlockchainName;
    public final int toBlockGlobalNumber;
    public final byte[] toBlockHash32;

    public TextBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("TextBody version must be 1, got=" + (this.version & 0xFFFF));
        }

        if (!isValidSubType(this.subType)) {
            throw new IllegalArgumentException("Bad Text subType: " + (this.subType & 0xFFFF));
        }

        // минимум: line(4+32+4) + textLen(2)
        if (bodyBytes.length < 4 + 32 + 4 + 2) {
            throw new IllegalArgumentException("TextBody too short");
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        this.prevLineNumber = bb.getInt();

        this.prevLineHash32 = new byte[32];
        bb.get(this.prevLineHash32);

        this.thisLineNumber = bb.getInt();

        int textLen = Short.toUnsignedInt(bb.getShort());
        if (textLen <= 0) throw new IllegalArgumentException("Text payload is empty");
        if (bb.remaining() < textLen) throw new IllegalArgumentException("Text payload too short (len=" + textLen + ")");

        byte[] textBytes = new byte[textLen];
        bb.get(textBytes);

        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            this.message = decoder.decode(ByteBuffer.wrap(textBytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Text payload is not valid UTF-8", e);
        }

        if (this.message.isBlank()) throw new IllegalArgumentException("Text message is blank");

        // target only for reply/repost/edit
        if (isHasTargetSubType(this.subType)) {
            if (bb.remaining() < 1) throw new IllegalArgumentException("Missing toBlockchainNameLen");

            int nameLen = Byte.toUnsignedInt(bb.get());
            if (nameLen <= 0) throw new IllegalArgumentException("toBlockchainNameLen is 0");
            if (bb.remaining() < nameLen + 4 + 32)
                throw new IllegalArgumentException("Reply/Repost/Edit payload too short");

            byte[] nameBytes = new byte[nameLen];
            bb.get(nameBytes);
            this.toBlockchainName = new String(nameBytes, StandardCharsets.UTF_8);

            this.toBlockGlobalNumber = bb.getInt();

            this.toBlockHash32 = new byte[32];
            bb.get(this.toBlockHash32);

            if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());

        } else {
            this.toBlockchainName = null;
            this.toBlockGlobalNumber = 0;
            this.toBlockHash32 = null;

            if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail for subType=NEW, remaining=" + bb.remaining());
        }
    }

    public TextBody(int prevLineNumber,
                   byte[] prevLineHash32,
                   int thisLineNumber,
                   short subType,
                   String message,
                   String toBlockchainName,
                   Integer toBlockGlobalNumber,
                   byte[] toBlockHash32) {

        Objects.requireNonNull(message, "message == null");
        if (!isValidSubType(subType)) throw new IllegalArgumentException("Bad Text subType: " + (subType & 0xFFFF));
        if (message.isBlank()) throw new IllegalArgumentException("message is blank");

        this.prevLineNumber = prevLineNumber;
        this.prevLineHash32 = (prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        this.thisLineNumber = thisLineNumber;

        this.subType = subType;
        this.version = VER;

        this.message = message;

        if (isHasTargetSubType(subType)) {
            Objects.requireNonNull(toBlockchainName, "toBlockchainName == null");
            Objects.requireNonNull(toBlockGlobalNumber, "toBlockGlobalNumber == null");
            Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");
            if (toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
            if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
            if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

            this.toBlockchainName = toBlockchainName;
            this.toBlockGlobalNumber = toBlockGlobalNumber;
            this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);
        } else {
            this.toBlockchainName = null;
            this.toBlockGlobalNumber = 0;
            this.toBlockHash32 = null;
        }
    }

    private static boolean isValidSubType(short st) {
        int v = st & 0xFFFF;
        return v == (MsgSubType.TEXT_NEW & 0xFFFF)
                || v == (MsgSubType.TEXT_REPLY & 0xFFFF)
                || v == (MsgSubType.TEXT_REPOST & 0xFFFF)
                || v == (MsgSubType.TEXT_EDIT & 0xFFFF);
    }

    private static boolean isHasTargetSubType(short st) {
        int v = st & 0xFFFF;
        return v == (MsgSubType.TEXT_REPLY & 0xFFFF)
                || v == (MsgSubType.TEXT_REPOST & 0xFFFF)
                || v == (MsgSubType.TEXT_EDIT & 0xFFFF);
    }

    @Override
    public TextBody check() {
        if (!isValidSubType(subType)) throw new IllegalArgumentException("Bad Text subType: " + (subType & 0xFFFF));
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Text message is blank");

        // line fields rule:
        if (prevLineNumber == -1) {
            if (!isAllZero32(prevLineHash32)) throw new IllegalArgumentException("prevLineHash32 must be zero when prevLineNumber=-1");
            if (thisLineNumber != -1) throw new IllegalArgumentException("thisLineNumber must be -1 when prevLineNumber=-1");
        } else {
            if (prevLineHash32 == null || prevLineHash32.length != 32) throw new IllegalArgumentException("prevLineHash32 invalid");
            // thisLineNumber сервер пока не проверяет (принимаем как есть)
        }

        if (isHasTargetSubType(subType)) {
            if (toBlockchainName == null || toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
            if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
            if (toBlockHash32 == null || toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 invalid");
        } else {
            if (toBlockchainName != null || toBlockHash32 != null) throw new IllegalArgumentException("SUB_NEW must not contain target fields");
        }

        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] msgUtf8 = message.getBytes(StandardCharsets.UTF_8);
        if (msgUtf8.length == 0) throw new IllegalArgumentException("Text payload is empty");
        if (msgUtf8.length > 65535) throw new IllegalArgumentException("Text too long (>65535 bytes)");

        int cap = 4 + 32 + 4  // line fields
                + 2 + msgUtf8.length; // text

        byte[] nameBytes = null;

        if (isHasTargetSubType(subType)) {
            nameBytes = toBlockchainName.getBytes(StandardCharsets.UTF_8);
            if (nameBytes.length == 0 || nameBytes.length > 255)
                throw new IllegalArgumentException("toBlockchainName utf8 len must be 1..255");
            if (toBlockHash32 == null || toBlockHash32.length != 32)
                throw new IllegalArgumentException("toBlockHash32 != 32");

            cap += 1 + nameBytes.length + 4 + 32;
        }

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putInt(prevLineNumber);
        bb.put(prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        bb.putInt(thisLineNumber);

        bb.putShort((short) msgUtf8.length);
        bb.put(msgUtf8);

        if (isHasTargetSubType(subType)) {
            bb.put((byte) nameBytes.length);
            bb.put(nameBytes);
            bb.putInt(toBlockGlobalNumber);
            bb.put(toBlockHash32);
        }

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
    @Override public String toLogin() { return null; }

    @Override
    public String toBchName() {
        return isHasTargetSubType(subType) ? toBlockchainName : null;
    }

    @Override
    public Integer toBlockGlobalNumber() {
        return isHasTargetSubType(subType) ? toBlockGlobalNumber : null;
    }

    @Override
    public byte[] toBlockHasheBytes() {
        return isHasTargetSubType(subType) ? toBlockHash32 : null;
    }
}