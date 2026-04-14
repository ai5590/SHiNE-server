package server.logic.ws_protocol.JSON.messages;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class SignedDirectMessagePacket {
    static final byte[] PREFIX = "SHiNE_msg".getBytes(StandardCharsets.US_ASCII);
    static final int VERSION = 1;
    static final int MESSAGE_TYPE_DIRECT = 1;
    static final int TARGET_ALL_SESSIONS = 0;
    static final int TARGET_ONE_SESSION = 1;

    final int version;
    final String toLogin;
    final String fromLogin;
    final long timeMs;
    final long nonce;
    final int messageType;
    final int targetMode;
    final String targetSessionId;
    final byte[] messageBytes;
    final byte[] signedBody;
    final byte[] signature64;
    final byte[] rawPacket;

    private SignedDirectMessagePacket(
            int version,
            String toLogin,
            String fromLogin,
            long timeMs,
            long nonce,
            int messageType,
            int targetMode,
            String targetSessionId,
            byte[] messageBytes,
            byte[] signedBody,
            byte[] signature64,
            byte[] rawPacket
    ) {
        this.version = version;
        this.toLogin = toLogin;
        this.fromLogin = fromLogin;
        this.timeMs = timeMs;
        this.nonce = nonce;
        this.messageType = messageType;
        this.targetMode = targetMode;
        this.targetSessionId = targetSessionId;
        this.messageBytes = messageBytes;
        this.signedBody = signedBody;
        this.signature64 = signature64;
        this.rawPacket = rawPacket;
    }

    static SignedDirectMessagePacket parse(byte[] raw, int maxMessageBytes) {
        if (raw == null || raw.length < PREFIX.length + 1 + 1 + 1 + 8 + 4 + 2 + 1 + 2 + 64) {
            throw new IllegalArgumentException("BAD_LEN");
        }
        if (raw.length > 4096) {
            throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
        }

        ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        byte[] prefixBytes = new byte[PREFIX.length];
        bb.get(prefixBytes);
        if (!Arrays.equals(prefixBytes, PREFIX)) {
            throw new IllegalArgumentException("BAD_PREFIX");
        }

        int version = Byte.toUnsignedInt(bb.get());
        if (version != VERSION) {
            throw new IllegalArgumentException("BAD_VERSION");
        }

        String toLogin = readAscii(bb, 1, 30, "BAD_TO_LOGIN");
        String fromLogin = readAscii(bb, 1, 30, "BAD_FROM_LOGIN");

        long timeMs = bb.getLong();
        if (timeMs < 0) throw new IllegalArgumentException("BAD_TIME");

        long nonce = Integer.toUnsignedLong(bb.getInt());

        int messageType = Short.toUnsignedInt(bb.getShort());
        if (messageType != MESSAGE_TYPE_DIRECT) {
            throw new IllegalArgumentException("BAD_MESSAGE_TYPE");
        }

        int targetMode = Byte.toUnsignedInt(bb.get());
        if (targetMode != TARGET_ALL_SESSIONS && targetMode != TARGET_ONE_SESSION) {
            throw new IllegalArgumentException("BAD_TARGET_MODE");
        }

        String targetSessionId = null;
        if (targetMode == TARGET_ONE_SESSION) {
            targetSessionId = readAscii(bb, 1, 255, "BAD_SESSION_ID");
        }

        int msgLen = Short.toUnsignedInt(bb.getShort());
        if (msgLen < 1 || msgLen > maxMessageBytes) {
            throw new IllegalArgumentException("BAD_MESSAGE_LEN");
        }
        if (bb.remaining() != msgLen + 64) {
            throw new IllegalArgumentException("BAD_LEN");
        }

        byte[] messageBytes = new byte[msgLen];
        bb.get(messageBytes);

        byte[] signature64 = new byte[64];
        bb.get(signature64);

        byte[] signedBody = Arrays.copyOf(raw, raw.length - 64);

        return new SignedDirectMessagePacket(
                version, toLogin, fromLogin, timeMs, nonce, messageType, targetMode,
                targetSessionId, messageBytes, signedBody, signature64, raw
        );
    }

    private static String readAscii(ByteBuffer bb, int minLen, int maxLen, String code) {
        if (!bb.hasRemaining()) throw new IllegalArgumentException(code);
        int len = Byte.toUnsignedInt(bb.get());
        if (len < minLen || len > maxLen || bb.remaining() < len) {
            throw new IllegalArgumentException(code);
        }
        byte[] bytes = new byte[len];
        bb.get(bytes);
        for (byte b : bytes) {
            if (b < 0x20 || b > 0x7E) throw new IllegalArgumentException(code);
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
