package blockchain.body;

import blockchain.MsgSubType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.Objects;

/**
 * CreateChannelBody — TECH сообщение создания канала.
 *
 * type=0, ver=1 (в заголовке блока)
 * subType=MsgSubType.TECH_CREATE_CHANNEL (=1)
 *
 * Это сообщение идёт по ТЕХ-ЛИНИИ (hasLine):
 *  - prevLineNumber/hash указывают на предыдущее TECH-сообщение (HEADER или прошлый CREATE_CHANNEL)
 *  - thisLineNumber: 1,2,3... (тех-нумерация)
 *
 * bodyBytes (BigEndian), новый формат line-prefix:
 *   [4]  lineCode         (для TECH линии обычно 0)
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *   [1]  channelNameLen (uint8)
 *   [N]  channelName UTF-8  (^[A-Za-z0-9_]+$)
 *
 * Важно:
 *  - канал "0" зарезервирован (создаётся по умолчанию от HEADER), создавать его нельзя.
 */
public final class CreateChannelBody implements BodyRecord, BodyHasLine {

    public static final short TYPE = 0;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    public static final short SUBTYPE = MsgSubType.TECH_CREATE_CHANNEL;

    private static final byte[] ZERO32 = new byte[32];
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 32;
    private static final Pattern ALLOWED_NAME_PATTERN =
            Pattern.compile("^[\\p{IsLatin}\\p{IsCyrillic}0-9 _-]+$");

    public final short subType;   // из header
    public final short version;   // из header

    // line
    public final int lineCode;
    public final int prevLineNumber;
    public final byte[] prevLineHash32; // 32
    public final int thisLineNumber;

    // payload
    public final String channelName;

    public CreateChannelBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("CreateChannelBody version must be 1, got=" + (this.version & 0xFFFF));
        }
        if ((this.subType & 0xFFFF) != (SUBTYPE & 0xFFFF)) {
            throw new IllegalArgumentException("CreateChannelBody subType must be TECH_CREATE_CHANNEL(1), got=" + (this.subType & 0xFFFF));
        }

        // минимум: lineCode(4) + line(4+32+4) + nameLen(1) + name(1)
        if (bodyBytes.length < 4 + (4 + 32 + 4) + 1 + 1) {
            throw new IllegalArgumentException("CreateChannelBody too short");
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        this.lineCode = bb.getInt();

        this.prevLineNumber = bb.getInt();

        this.prevLineHash32 = new byte[32];
        bb.get(this.prevLineHash32);

        this.thisLineNumber = bb.getInt();

        int nameLen = Byte.toUnsignedInt(bb.get());
        if (nameLen <= 0) throw new IllegalArgumentException("channelNameLen is 0");
        if (bb.remaining() != nameLen) {
            throw new IllegalArgumentException("CreateChannelBody tail mismatch: remaining=" + bb.remaining() + " nameLen=" + nameLen);
        }

        byte[] nameBytes = new byte[nameLen];
        bb.get(nameBytes);

        this.channelName = new String(nameBytes, StandardCharsets.UTF_8);

        if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
    }

    public CreateChannelBody(int lineCode,
                             int prevLineNumber,
                             byte[] prevLineHash32,
                             int thisLineNumber,
                             String channelName) {
        Objects.requireNonNull(channelName, "channelName == null");
        if (lineCode < 0) throw new IllegalArgumentException("lineCode < 0");

        this.subType = SUBTYPE;
        this.version = VER;

        this.lineCode = lineCode;
        this.prevLineNumber = prevLineNumber;
        this.prevLineHash32 = (prevLineHash32 == null ? ZERO32 : Arrays.copyOf(prevLineHash32, 32));
        this.thisLineNumber = thisLineNumber;

        this.channelName = channelName;
    }

    @Override
    public CreateChannelBody check() {
        if (lineCode < 0) throw new IllegalArgumentException("lineCode < 0");

        if ((subType & 0xFFFF) != (SUBTYPE & 0xFFFF))
            throw new IllegalArgumentException("CreateChannelBody subType must be TECH_CREATE_CHANNEL(1)");

        String normalizedName = normalizeDisplayName(channelName);
        if (normalizedName.isEmpty())
            throw new IllegalArgumentException("channelName is blank");
        int cpLen = normalizedName.codePointCount(0, normalizedName.length());
        // Backward compatibility for historical blocks:
        // strict create-channel rules are enforced in AddBlock handler (ChannelNameRules),
        // but parser-level check must allow legacy channel names during bootstrap/replay.
        if (cpLen > MAX_NAME_LENGTH)
            throw new IllegalArgumentException("channelName length must be <=32");

        // tech-line: prev обязателен (минимум HEADER=0)
        if (prevLineNumber < 0)
            throw new IllegalArgumentException("prevLineNumber must be >=0 for CreateChannelBody");
        if (prevLineHash32 == null || prevLineHash32.length != 32)
            throw new IllegalArgumentException("prevLineHash32 invalid");
        if (thisLineNumber <= 0)
            throw new IllegalArgumentException("thisLineNumber must be >=1 for CreateChannelBody");

        return this;
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    @Override
    public byte[] toBytes() {
        byte[] nameUtf8 = channelName.getBytes(StandardCharsets.UTF_8);
        if (nameUtf8.length == 0 || nameUtf8.length > 255)
            throw new IllegalArgumentException("channelName utf8 len must be 1..255");

        int cap = 4 + (4 + 32 + 4) + 1 + nameUtf8.length;
        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putInt(lineCode);

        bb.putInt(prevLineNumber);
        bb.put(prevLineHash32 == null ? ZERO32 : Arrays.copyOf(prevLineHash32, 32));
        bb.putInt(thisLineNumber);

        bb.put((byte) nameUtf8.length);
        bb.put(nameUtf8);

        return bb.array();
    }

    /* ====================== BodyHasLine ====================== */
    @Override public int lineCode() { return lineCode; }
    @Override public int prevLineBlockGlobalNumber() { return prevLineNumber; }
    @Override public byte[] prevLineBlockHash32() { return prevLineHash32 == null ? null : Arrays.copyOf(prevLineHash32, 32); }
    @Override public int lineSeq() { return thisLineNumber; }
}
