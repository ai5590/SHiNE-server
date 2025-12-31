package utils.crypto;

import org.bouncycastle.crypto.digests.SHA256Digest;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class HashSHA256Util {
    private HashSHA256Util() {}

    /** Посчитать SHA-256 от всего массива. */
    public static byte[] sha256(byte[] data) {
        if (data == null) throw new IllegalArgumentException("data == null");
        SHA256Digest d = new SHA256Digest();
        d.update(data, 0, data.length);
        byte[] out = new byte[32];
        d.doFinal(out, 0);
        return out;
    }

    /** Инкрементальный SHA-256 (если нужно будет кормить по кускам). */
    public static final class Sha256 {
        private final SHA256Digest d = new SHA256Digest();
        public Sha256 update(byte[] part) {
            if (part != null) d.update(part, 0, part.length);
            return this;
        }
        public byte[] doFinal() {
            byte[] out = new byte[32];
            d.doFinal(out, 0);
            return out;
        }
    }
}