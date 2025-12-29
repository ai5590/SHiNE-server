package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * ReactionBody — type=2, version=1.
 *
 * Сериализация bodyBytes:
 *   [2] type=2
 *   [2] ver=1
 *   [4] reactionCode (int32)
 *   [1] toBlockchainNameLen (uint8)
 *   [N] toBlockchainName UTF-8
 *   [4] toBlockGlobalNumber (int32)
 *   [32] toBlockHash (raw 32 bytes)
 *
 * ЛИНИЯ:
 *  - строго lineIndex=2
 *
 * ВАЖНО:
 *  - Здесь мы НЕ проверяем, существует ли цель реакции (MVP правило).
 */
public final class ReactionBody implements BodyRecord {

    public static final short TYPE = 2;
    public static final short VER  = 1;

    public final int reactionCode;
    public final String toBlockchainName;
    public final int toBlockGlobalNumber;
    public final byte[] toBlockHash32;

    public ReactionBody(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        if (bodyBytes.length < 4 + 4 + 1 + 1 + 4 + 32) {
            throw new IllegalArgumentException("ReactionBody too short");
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER)
            throw new IllegalArgumentException("Not ReactionBody: type=" + type + " ver=" + ver);

        this.reactionCode = bb.getInt();

        int nameLen = Byte.toUnsignedInt(bb.get());
        if (nameLen <= 0) throw new IllegalArgumentException("toBlockchainNameLen is 0");
        if (bb.remaining() < nameLen + 4 + 32) throw new IllegalArgumentException("ReactionBody payload too short");

        byte[] nameBytes = new byte[nameLen];
        bb.get(nameBytes);
        this.toBlockchainName = new String(nameBytes, StandardCharsets.UTF_8);

        this.toBlockGlobalNumber = bb.getInt();

        this.toBlockHash32 = new byte[32];
        bb.get(this.toBlockHash32);
    }

    public ReactionBody(int reactionCode, String toBlockchainName, int toBlockGlobalNumber, byte[] toBlockHash32) {
        Objects.requireNonNull(toBlockchainName, "toBlockchainName == null");
        Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");
        if (toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
        if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

        this.reactionCode = reactionCode;
        this.toBlockchainName = toBlockchainName;
        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);
    }

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }

    @Override
    public short expectedLineIndex() {
        return 2;
    }

    @Override
    public ReactionBody check() {
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

        int cap = 4 + 4 + 1 + nameBytes.length + 4 + 32;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
        bb.putShort(TYPE);
        bb.putShort(VER);
        bb.putInt(reactionCode);
        bb.put((byte) nameBytes.length);
        bb.put(nameBytes);
        bb.putInt(toBlockGlobalNumber);
        bb.put(toBlockHash32);

        return bb.array();
    }

    @Override
    public String toString() {
        return """
                ReactionBody {
                  тип записи              : REACTION (type=2, ver=1)
                  ожидаемая линия         : 2
                  код реакции             : %d
                  целевой блокчейн        : "%s"
                  globalNumber цели       : %d
                  hash цели (hex)         : %s
                }
                """.formatted(
                        reactionCode,
                        toBlockchainName,
                        toBlockGlobalNumber,
                        toBlockHashHex()
                );
    }

    public String toBlockHashHex() {
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[64];
        for (int i = 0; i < 32; i++) {
            int v = toBlockHash32[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}