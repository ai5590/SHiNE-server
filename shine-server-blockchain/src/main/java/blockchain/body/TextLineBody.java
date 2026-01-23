package blockchain.body;

import blockchain.MsgSubType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * TextLineBody — type=1, ver=1.
 *
 * subType:
 *  - POST      (10)
 *  - EDIT_POST (11)
 *
 * Формат bodyBytes (BigEndian):
 *
 * POST:
 *   [4]  lineCode
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *   [2]  textLenBytes (uint16)
 *   [N]  text UTF-8
 *
 * EDIT_POST:
 *   [4]  lineCode
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *   [4]  toBlockGlobalNumber (int32)
 *   [32] toBlockHash32
 *   [2]  textLenBytes (uint16)
 *   [N]  text UTF-8
 */
public final class TextLineBody implements BodyRecord, BodyHasLine, BodyHasTarget {

    public static final short TYPE = 1;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    public final short subType;   // из header
    public final short version;   // из header (=1)

    // line
    public final int lineCode;
    public final int prevLineNumber;
    public final byte[] prevLineHash32; // 32 (может быть нули)
    public final int thisLineNumber;

    // target (только для EDIT_POST)
    public final Integer toBlockGlobalNumber; // nullable для POST
    public final byte[] toBlockHash32;        // nullable для POST

    // text
    public final String message;

    /* ====================== parse from bytes ====================== */

    public TextLineBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("TextLineBody version must be 1, got=" + (this.version & 0xFFFF));
        }

        int st = this.subType & 0xFFFF;
        if (st != (MsgSubType.TEXT_POST & 0xFFFF) && st != (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            throw new IllegalArgumentException("TextLineBody supports only POST/EDIT_POST, got subType=" + st);
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        // минимум line + textLen(2)
        ensureMin(bb, (4 + 4 + 32 + 4) + 2, "TextLineBody too short");

        this.lineCode = bb.getInt();
        this.prevLineNumber = bb.getInt();

        this.prevLineHash32 = new byte[32];
        bb.get(this.prevLineHash32);

        this.thisLineNumber = bb.getInt();

        if (st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            // нужен target
            ensureMin(bb, (4 + 32) + 2, "EDIT_POST missing target");
            int tgtNum = bb.getInt();
            byte[] tgtHash = new byte[32];
            bb.get(tgtHash);

            this.toBlockGlobalNumber = tgtNum;
            this.toBlockHash32 = tgtHash;

        } else {
            this.toBlockGlobalNumber = null;
            this.toBlockHash32 = null;
        }

        this.message = readStrictUtf8Len16(bb, "TextLineBody text");

        ensureNoTail(bb, "TextLineBody");
    }

    /* ====================== manual ctor ====================== */

    public TextLineBody(int lineCode,
                        int prevLineNumber,
                        byte[] prevLineHash32,
                        int thisLineNumber,
                        short subType,
                        Integer toBlockGlobalNumber,
                        byte[] toBlockHash32,
                        String message) {

        Objects.requireNonNull(message, "message == null");

        int st = subType & 0xFFFF;
        if (st != (MsgSubType.TEXT_POST & 0xFFFF) && st != (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            throw new IllegalArgumentException("TextLineBody supports only POST/EDIT_POST");
        }

        if (lineCode < 0) throw new IllegalArgumentException("lineCode < 0");
        if (message.isBlank()) throw new IllegalArgumentException("message is blank");

        this.subType = subType;
        this.version = VER;

        this.lineCode = lineCode;
        this.prevLineNumber = prevLineNumber;
        this.prevLineHash32 = (prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        this.thisLineNumber = thisLineNumber;

        if (st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            Objects.requireNonNull(toBlockGlobalNumber, "toBlockGlobalNumber == null");
            Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");
            if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
            if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

            this.toBlockGlobalNumber = toBlockGlobalNumber;
            this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);
        } else {
            this.toBlockGlobalNumber = null;
            this.toBlockHash32 = null;
        }

        this.message = message;
    }

    @Override
    public TextLineBody check() {
        int st = subType & 0xFFFF;
        if (st != (MsgSubType.TEXT_POST & 0xFFFF) && st != (MsgSubType.TEXT_EDIT_POST & 0xFFFF))
            throw new IllegalArgumentException("Bad TextLineBody subType: " + st);

        if (lineCode < 0) throw new IllegalArgumentException("lineCode < 0");
        if (prevLineHash32 == null || prevLineHash32.length != 32)
            throw new IllegalArgumentException("prevLineHash32 invalid");

        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Text message is blank");

        if (st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            if (toBlockGlobalNumber == null || toBlockGlobalNumber < 0)
                throw new IllegalArgumentException("EDIT_POST toBlockGlobalNumber invalid");
            if (toBlockHash32 == null || toBlockHash32.length != 32)
                throw new IllegalArgumentException("EDIT_POST toBlockHash32 invalid");
        } else {
            if (toBlockGlobalNumber != null || toBlockHash32 != null)
                throw new IllegalArgumentException("POST must not contain target fields");
        }

        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] msgUtf8 = message.getBytes(StandardCharsets.UTF_8);
        if (msgUtf8.length == 0) throw new IllegalArgumentException("Text payload is empty");
        if (msgUtf8.length > 65535) throw new IllegalArgumentException("Text too long (>65535 bytes)");

        int st = subType & 0xFFFF;

        int cap;
        if (st == (MsgSubType.TEXT_POST & 0xFFFF)) {
            cap = (4 + 4 + 32 + 4) + 2 + msgUtf8.length;
        } else {
            // EDIT_POST
            if (toBlockGlobalNumber == null) throw new IllegalArgumentException("EDIT_POST missing toBlockGlobalNumber");
            if (toBlockHash32 == null || toBlockHash32.length != 32) throw new IllegalArgumentException("EDIT_POST toBlockHash32 != 32");
            cap = (4 + 4 + 32 + 4) + (4 + 32) + 2 + msgUtf8.length;
        }

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putInt(lineCode);
        bb.putInt(prevLineNumber);
        bb.put(prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        bb.putInt(thisLineNumber);

        if (st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            bb.putInt(toBlockGlobalNumber);
            bb.put(toBlockHash32);
        }

        bb.putShort((short) msgUtf8.length);
        bb.put(msgUtf8);

        return bb.array();
    }

    /* ====================== BodyHasLine ====================== */
    @Override public int lineCode() { return lineCode; }
    @Override public int prevLineBlockGlobalNumber() { return prevLineNumber; }
    @Override public byte[] prevLineBlockHash32() { return Arrays.copyOf(prevLineHash32, 32); }
    @Override public int lineSeq() { return thisLineNumber; }

    /* ====================== BodyHasTarget ===================== */
    @Override public String toBchName() { return null; } // по ТЗ: не хранить
    @Override public Integer toBlockGlobalNumber() { return toBlockGlobalNumber; }
    @Override public byte[] toBlockHashBytes() { return toBlockHash32; }

    /* ====================== helpers ====================== */

    public boolean isEditPost() {
        return (subType & 0xFFFF) == (MsgSubType.TEXT_EDIT_POST & 0xFFFF);
    }

    private static String readStrictUtf8Len16(ByteBuffer bb, String fieldName) {
        int len = Short.toUnsignedInt(bb.getShort());
        if (len <= 0) throw new IllegalArgumentException(fieldName + " is empty");
        if (bb.remaining() < len) throw new IllegalArgumentException(fieldName + " payload too short (len=" + len + ")");

        byte[] bytes = new byte[len];
        bb.get(bytes);

        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            String s = decoder.decode(ByteBuffer.wrap(bytes)).toString();
            if (s.isBlank()) throw new IllegalArgumentException(fieldName + " is blank");
            return s;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(fieldName + " is not valid UTF-8", e);
        }
    }

    private static void ensureMin(ByteBuffer bb, int need, String msg) {
        if (bb.remaining() < need) throw new IllegalArgumentException(msg + " (need=" + need + ", remaining=" + bb.remaining() + ")");
    }

    private static void ensureNoTail(ByteBuffer bb, String ctx) {
        if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes for " + ctx + ", remaining=" + bb.remaining());
    }
}