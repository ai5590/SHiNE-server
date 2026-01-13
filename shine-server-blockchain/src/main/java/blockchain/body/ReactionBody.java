// =======================
// blockchain/body/ReactionBody.java   (ИЗМЕНЁННЫЙ: bodyBytes без type/subType/version, НЕТ линейных полей)
// =======================
package blockchain.body;

import shine.db.MsgSubType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * ReactionBody — type=2, version=1 (в заголовке блока).
 *
 * subType (в заголовке блока):
 *   1 = LIKE
 *
 * bodyBytes (BigEndian), новый формат:
 *   [1] toBlockchainNameLen (uint8)
 *   [N] toBlockchainName UTF-8
 *   [4] toBlockGlobalNumber (int32)
 *   [32] toBlockHash32 (raw 32 bytes)
 *
 * ЛИНИИ НЕТ.
 */
public final class ReactionBody implements BodyRecord, BodyHasTarget {

    public static final short TYPE = 2;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    public final short subType;   // из header
    public final short version;   // из header

    public final String toBlockchainName;
    public final int toBlockGlobalNumber;
    public final byte[] toBlockHash32;

    public ReactionBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("ReactionBody version must be 1, got=" + (this.version & 0xFFFF));
        }
        if ((this.subType & 0xFFFF) != (MsgSubType.REACTION_LIKE & 0xFFFF)) {
            throw new IllegalArgumentException("Bad reaction subType: " + (this.subType & 0xFFFF));
        }

        // минимум: nameLen[1]+name[1]+global[4]+hash[32]
        if (bodyBytes.length < 1 + 1 + 4 + 32) throw new IllegalArgumentException("ReactionBody too short");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        int nameLen = Byte.toUnsignedInt(bb.get());
        if (nameLen <= 0) throw new IllegalArgumentException("toBlockchainNameLen is 0");
        if (bb.remaining() < nameLen + 4 + 32) throw new IllegalArgumentException("ReactionBody payload too short");

        byte[] nameBytes = new byte[nameLen];
        bb.get(nameBytes);
        this.toBlockchainName = new String(nameBytes, StandardCharsets.UTF_8);

        this.toBlockGlobalNumber = bb.getInt();

        this.toBlockHash32 = new byte[32];
        bb.get(this.toBlockHash32);

        if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
    }

    public ReactionBody(String toBlockchainName, int toBlockGlobalNumber, byte[] toBlockHash32) {
        Objects.requireNonNull(toBlockchainName, "toBlockchainName == null");
        Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");

        this.subType = MsgSubType.REACTION_LIKE;
        this.version = VER;

        if (toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
        if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

        this.toBlockchainName = toBlockchainName;
        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);
    }

    @Override
    public ReactionBody check() {
        if ((subType & 0xFFFF) != (MsgSubType.REACTION_LIKE & 0xFFFF))
            throw new IllegalArgumentException("Bad reaction subType: " + (subType & 0xFFFF));

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
        byte[] nameBytes = toBlockchainName.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length == 0 || nameBytes.length > 255)
            throw new IllegalArgumentException("toBlockchainName utf8 len must be 1..255");

        int cap = 1 + nameBytes.length + 4 + 32;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) nameBytes.length);
        bb.put(nameBytes);
        bb.putInt(toBlockGlobalNumber);
        bb.put(toBlockHash32);

        return bb.array();
    }

    /* ====================== BodyHasTarget ====================== */

    @Override public String toLogin() { return null; }

    @Override public String toBchName() { return toBlockchainName; }

    @Override public Integer toBlockGlobalNumber() { return toBlockGlobalNumber; }

    @Override public byte[] toBlockHasheBytes() { return toBlockHash32; }
}