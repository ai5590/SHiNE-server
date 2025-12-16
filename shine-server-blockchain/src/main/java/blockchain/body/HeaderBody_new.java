package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * HeaderBody_new — type=0, version=1.
 *
 * Полный bodyBytes:
 *   [2] type=0
 *   [2] version=1
 *   [payload...]
 *
 * Payload (как у текущего HeaderBody):
 *   [8]  tag ASCII "SHiNE001"
 *   [8]  blockchainId (long BE)
 *   [1]  loginLength=N (uint8)
 *   [N]  userLogin UTF-8
 *   [4]  blockchainType (int BE)  (резерв)
 *   [4]  blockchainNumber (int BE) (резерв)
 *   [2]  versionUserBch (short BE) (резерв)
 *   [8]  prevUserBchId (long BE)   (резерв)
 *   [32] publicKey32 (raw)
 */
public final class HeaderBody_new implements BodyRecord_new {

    public static final short TYPE = 0;
    public static final short VER  = 1;

    public static final String TAG = "SHiNE001";
    public static final int PUBKEY_LEN = 32;

    public final String tag; // "SHiNE001"
    public final long blockchainId;
    public final String userLogin;
    public final int blockchainType;
    public final int blockchainNumber;
    public final short versionUserBch;
    public final long prevUserBchId;
    public final byte[] publicKey32;

    /**
     * Десериализация из полного bodyBytes (ВКЛЮЧАЯ первые 4 байта type/version).
     */
    public HeaderBody_new(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        if (bodyBytes.length < 4) throw new IllegalArgumentException("HeaderBody_new too short");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);
        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER)
            throw new IllegalArgumentException("Not HeaderBody_new: type=" + type + " ver=" + ver);

        // Теперь bb стоит на payload
        if (bb.remaining() < 8 + 8 + 1 + 4 + 4 + 2 + 8 + 32)
            throw new IllegalArgumentException("Header payload too short");

        byte[] tagBytes = new byte[8];
        bb.get(tagBytes);
        String t = new String(tagBytes, StandardCharsets.US_ASCII);
        if (!TAG.equals(t)) throw new IllegalArgumentException("Bad tag: " + t);
        this.tag = t;

        this.blockchainId = bb.getLong();

        int loginLen = Byte.toUnsignedInt(bb.get());
        if (loginLen <= 0 || bb.remaining() < loginLen + 4 + 4 + 2 + 8 + 32)
            throw new IllegalArgumentException("Bad login length");

        byte[] loginBytes = new byte[loginLen];
        bb.get(loginBytes);
        this.userLogin = new String(loginBytes, StandardCharsets.UTF_8);

        this.blockchainType   = bb.getInt();
        this.blockchainNumber = bb.getInt();
        this.versionUserBch   = bb.getShort();
        this.prevUserBchId    = bb.getLong();

        this.publicKey32 = new byte[PUBKEY_LEN];
        bb.get(this.publicKey32);
    }

    /**
     * Создание “вручную” (для генерации первого блока).
     */
    public HeaderBody_new(long blockchainId,
                          String userLogin,
                          int blockchainType,
                          int blockchainNumber,
                          short versionUserBch,
                          long prevUserBchId,
                          byte[] publicKey32) {

        Objects.requireNonNull(userLogin, "userLogin == null");
        Objects.requireNonNull(publicKey32, "publicKey32 == null");
        if (publicKey32.length != PUBKEY_LEN)
            throw new IllegalArgumentException("publicKey32 must be 32 bytes");

        this.tag = TAG;
        this.blockchainId = blockchainId;
        this.userLogin = userLogin;
        this.blockchainType = blockchainType;
        this.blockchainNumber = blockchainNumber;
        this.versionUserBch = versionUserBch;
        this.prevUserBchId = prevUserBchId;
        this.publicKey32 = Arrays.copyOf(publicKey32, PUBKEY_LEN);
    }

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }

    @Override
    public HeaderBody_new check() {
        if (userLogin == null || userLogin.isBlank())
            throw new IllegalArgumentException("Login is blank");
        if (!userLogin.matches("^[A-Za-z0-9_]+$"))
            throw new IllegalArgumentException("Login must match ^[A-Za-z0-9_]+$");
        if (publicKey32 == null || publicKey32.length != PUBKEY_LEN)
            throw new IllegalArgumentException("publicKey32 must be 32 bytes");
        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] loginUtf8 = userLogin.getBytes(StandardCharsets.UTF_8);
        if (loginUtf8.length > 255)
            throw new IllegalArgumentException("Login too long (>255 bytes)");

        int payloadCap = 8 + 8 + 1 + loginUtf8.length + 4 + 4 + 2 + 8 + 32;
        int cap = 4 + payloadCap;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        // [type/version]
        bb.putShort(TYPE);
        bb.putShort(VER);

        // payload
        bb.put(TAG.getBytes(StandardCharsets.US_ASCII)); // [8]
        bb.putLong(blockchainId);                        // [8]
        bb.put((byte) loginUtf8.length);                 // [1]
        bb.put(loginUtf8);                               // [N]
        bb.putInt(blockchainType);                       // [4]
        bb.putInt(blockchainNumber);                     // [4]
        bb.putShort(versionUserBch);                     // [2]
        bb.putLong(prevUserBchId);                       // [8]
        bb.put(publicKey32);                             // [32]

        return bb.array();
    }
}