package utils.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * BchCryptoVerifier — проверка хэша и подписи Ed25519 для .bch сущностей.
 *.
 * Канонический пре-имидж:
 *   [N]  userLogin UTF-8  (без длины! строго как байты строки)
 *   [8]  blockchainId (big-endian long)
 *   [32] prevHash32
 *   [*]  rawBytes (без подписи и без хэша)
 *.
 * Проверяем:
 *   • hash32 == SHA-256(preimage)
 *   • signature64 валидна как Ed25519(preimage, publicKey32)
 */
public final class BchCryptoVerifier {

    private static final Logger log = LoggerFactory.getLogger(BchCryptoVerifier.class);

    private BchCryptoVerifier() {}

    public static boolean verifyAll(String userLogin,
                                    long blockchainId,
                                    byte[] prevHash32,
                                    byte[] rawBytes,
                                    byte[] signature64,
                                    byte[] hash32,
                                    byte[] publicKey32) {
        try {
            Objects.requireNonNull(userLogin, "userLogin");
            requireLen(prevHash32, 32, "prevHash32");
            requireLen(signature64, 64, "signature64");
            requireLen(hash32, 32, "hash32");
            requireLen(publicKey32, 32, "publicKey32");
            Objects.requireNonNull(rawBytes, "rawBytes");

            byte[] preimage = buildPreimage(userLogin, blockchainId, prevHash32, rawBytes);

            // 1) Проверка хэша (BC)
            byte[] calcHash = HashSHA256Util.sha256(preimage);
            boolean hashOk = Arrays.equals(calcHash, hash32);

            // 2) Проверка подписи Ed25519
            boolean sigOk = Ed25519Util.verify(preimage, signature64, publicKey32);

            if (!hashOk) log.warn("Hash mismatch: hash32 != SHA-256(preimage)");
            if (!sigOk)  log.warn("Signature mismatch: Ed25519 verify failed");
            return hashOk && sigOk;
        } catch (IllegalArgumentException ex) {
            log.error("verifyAll: bad arguments", ex);
            return false;
        }
    }

    /** Собрать канонический пре-имидж без длины логина. */
    public static byte[] buildPreimage(String userLogin,
                                       long blockchainId,
                                       byte[] prevHash32,
                                       byte[] rawBytes) {
        Objects.requireNonNull(userLogin, "userLogin");
        Objects.requireNonNull(prevHash32, "prevHash32");
        Objects.requireNonNull(rawBytes, "rawBytes");

        byte[] loginUtf8 = userLogin.getBytes(StandardCharsets.UTF_8);
        requireLen(prevHash32, 32, "prevHash32");

        int capacity = loginUtf8.length + 8 + 32 + rawBytes.length;
        ByteBuffer buf = ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN);
        buf.put(loginUtf8);
        buf.putLong(blockchainId);
        buf.put(prevHash32);
        buf.put(rawBytes);
        return buf.array();
    }

    private static void requireLen(byte[] arr, int len, String name) {
        if (arr == null) throw new IllegalArgumentException(name + " is null");
        if (arr.length != len) {
            throw new IllegalArgumentException(name + " length != " + len + " (got " + arr.length + ")");
        }
    }
}
