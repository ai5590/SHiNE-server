package blockchain.body;

import blockchain.MsgSubType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * TECH body for create channel.
 *
 * v1 body bytes:
 *   [4]  lineCode
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *   [1]  channelNameLen
 *   [N]  channelName UTF-8
 *
 * v2 body bytes:
 *   [4]  lineCode
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *   [1]  channelNameLen
 *   [N]  channelName UTF-8
 *   [2]  channelDescriptionLen
 *   [M]  channelDescription UTF-8 (0..200 bytes)
 */
public final class CreateChannelBody implements BodyRecord, BodyHasLine {

    public static final short TYPE = 0;
    public static final short VER = 1;
    public static final short VER2 = 2;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);
    public static final int KEY_V2 = ((TYPE & 0xFFFF) << 16) | (VER2 & 0xFFFF);

    public static final short SUBTYPE = MsgSubType.TECH_CREATE_CHANNEL;

    private static final byte[] ZERO32 = new byte[32];
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_DESCRIPTION_UTF8_LEN = 200;

    public final short subType;
    public final short version;

    public final int lineCode;
    public final int prevLineNumber;
    public final byte[] prevLineHash32;
    public final int thisLineNumber;

    public final String channelName;
    public final String channelDescription;

    public CreateChannelBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        int ver = this.version & 0xFFFF;
        if (ver != (VER & 0xFFFF) && ver != (VER2 & 0xFFFF)) {
            throw new IllegalArgumentException("CreateChannelBody version must be 1 or 2, got=" + ver);
        }
        if ((this.subType & 0xFFFF) != (SUBTYPE & 0xFFFF)) {
            throw new IllegalArgumentException("CreateChannelBody subType must be TECH_CREATE_CHANNEL(1), got=" + (this.subType & 0xFFFF));
        }

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
        if (bb.remaining() < nameLen) {
            throw new IllegalArgumentException("CreateChannelBody tail mismatch: remaining=" + bb.remaining() + " nameLen=" + nameLen);
        }

        byte[] nameBytes = new byte[nameLen];
        bb.get(nameBytes);
        this.channelName = new String(nameBytes, StandardCharsets.UTF_8);

        if (ver == (VER2 & 0xFFFF)) {
            if (bb.remaining() < 2) {
                throw new IllegalArgumentException("CreateChannelBody v2 missing channelDescriptionLen");
            }

            int descriptionLen = Short.toUnsignedInt(bb.getShort());
            if (descriptionLen > MAX_DESCRIPTION_UTF8_LEN) {
                throw new IllegalArgumentException("channelDescription utf8 len must be <=200");
            }
            if (bb.remaining() != descriptionLen) {
                throw new IllegalArgumentException("CreateChannelBody v2 tail mismatch: remaining=" + bb.remaining() + " descriptionLen=" + descriptionLen);
            }

            if (descriptionLen == 0) {
                this.channelDescription = "";
            } else {
                byte[] descriptionBytes = new byte[descriptionLen];
                bb.get(descriptionBytes);
                this.channelDescription = normalizeDescription(new String(descriptionBytes, StandardCharsets.UTF_8));
            }
            if (bb.remaining() != 0) {
                throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
            }
            return;
        }

        this.channelDescription = "";
        if (bb.remaining() != 0) {
            throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
        }
    }

    public CreateChannelBody(int lineCode,
                             int prevLineNumber,
                             byte[] prevLineHash32,
                             int thisLineNumber,
                             String channelName) {
        this(lineCode, prevLineNumber, prevLineHash32, thisLineNumber, channelName, "", VER);
    }

    public CreateChannelBody(int lineCode,
                             int prevLineNumber,
                             byte[] prevLineHash32,
                             int thisLineNumber,
                             String channelName,
                             String channelDescription) {
        this(lineCode, prevLineNumber, prevLineHash32, thisLineNumber, channelName, channelDescription, VER2);
    }

    private CreateChannelBody(int lineCode,
                              int prevLineNumber,
                              byte[] prevLineHash32,
                              int thisLineNumber,
                              String channelName,
                              String channelDescription,
                              short version) {
        Objects.requireNonNull(channelName, "channelName == null");
        if (lineCode < 0) throw new IllegalArgumentException("lineCode < 0");

        this.subType = SUBTYPE;
        this.version = version;

        this.lineCode = lineCode;
        this.prevLineNumber = prevLineNumber;
        this.prevLineHash32 = (prevLineHash32 == null ? ZERO32 : Arrays.copyOf(prevLineHash32, 32));
        this.thisLineNumber = thisLineNumber;

        this.channelName = channelName;
        this.channelDescription = channelDescription == null ? "" : channelDescription;
    }

    @Override
    public CreateChannelBody check() {
        if (lineCode < 0) throw new IllegalArgumentException("lineCode < 0");

        if ((subType & 0xFFFF) != (SUBTYPE & 0xFFFF)) {
            throw new IllegalArgumentException("CreateChannelBody subType must be TECH_CREATE_CHANNEL(1)");
        }

        String normalizedName = normalizeDisplayName(channelName);
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("channelName is blank");
        }

        int cpLen = normalizedName.codePointCount(0, normalizedName.length());
        if (cpLen > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("channelName length must be <=32");
        }

        String normalizedDescription = normalizeDescription(channelDescription);
        byte[] descUtf8 = normalizedDescription.getBytes(StandardCharsets.UTF_8);
        if (descUtf8.length > MAX_DESCRIPTION_UTF8_LEN) {
            throw new IllegalArgumentException("channelDescription utf8 len must be <=200");
        }

        if (prevLineNumber < 0) {
            throw new IllegalArgumentException("prevLineNumber must be >=0 for CreateChannelBody");
        }
        if (prevLineHash32 == null || prevLineHash32.length != 32) {
            throw new IllegalArgumentException("prevLineHash32 invalid");
        }
        if (thisLineNumber <= 0) {
            throw new IllegalArgumentException("thisLineNumber must be >=1 for CreateChannelBody");
        }

        return this;
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeDescription(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    @Override
    public byte[] toBytes() {
        byte[] nameUtf8 = normalizeDisplayName(channelName).getBytes(StandardCharsets.UTF_8);
        if (nameUtf8.length == 0 || nameUtf8.length > 255) {
            throw new IllegalArgumentException("channelName utf8 len must be 1..255");
        }

        boolean isV2 = (version & 0xFFFF) == (VER2 & 0xFFFF);
        byte[] descriptionUtf8 = normalizeDescription(channelDescription).getBytes(StandardCharsets.UTF_8);
        if (descriptionUtf8.length > MAX_DESCRIPTION_UTF8_LEN) {
            throw new IllegalArgumentException("channelDescription utf8 len must be <=200");
        }

        int cap = 4 + (4 + 32 + 4) + 1 + nameUtf8.length + (isV2 ? 2 + descriptionUtf8.length : 0);
        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putInt(lineCode);
        bb.putInt(prevLineNumber);
        bb.put(prevLineHash32 == null ? ZERO32 : Arrays.copyOf(prevLineHash32, 32));
        bb.putInt(thisLineNumber);

        bb.put((byte) nameUtf8.length);
        bb.put(nameUtf8);

        if (isV2) {
            bb.putShort((short) (descriptionUtf8.length & 0xFFFF));
            if (descriptionUtf8.length > 0) {
                bb.put(descriptionUtf8);
            }
        }

        return bb.array();
    }

    @Override
    public int lineCode() { return lineCode; }

    @Override
    public int prevLineBlockGlobalNumber() { return prevLineNumber; }

    @Override
    public byte[] prevLineBlockHash32() {
        return prevLineHash32 == null ? null : Arrays.copyOf(prevLineHash32, 32);
    }

    @Override
    public int lineSeq() { return thisLineNumber; }
}
