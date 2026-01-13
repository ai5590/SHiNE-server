// =======================
// blockchain/BchCryptoVerifier.java   (НОВАЯ ВЕРСИЯ под ТЗ)
// =======================
package blockchain;

import utils.crypto.Ed25519Util;

import java.security.MessageDigest;
import java.util.Objects;

/**
 * Новый верификатор по ТЗ:
 *
 * preimage = все байты блока без signature64
 * hash32   = SHA-256(preimage)
 * verify   = Ed25519.verify(hash32, signature64, pubKey32)
 */
public final class BchCryptoVerifier {

    private BchCryptoVerifier() {}

    public static byte[] sha256(byte[] data) {
        Objects.requireNonNull(data, "data == null");
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return d.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean verifyBlock(BchBlockEntry block, byte[] publicKey32) {
        Objects.requireNonNull(block, "block == null");
        Objects.requireNonNull(publicKey32, "publicKey32 == null");

        if (publicKey32.length != 32) throw new IllegalArgumentException("publicKey32 != 32");

        byte[] hash32 = block.getHash32();
        byte[] sig64 = block.getSignature64();

        return Ed25519Util.verify(hash32, sig64, publicKey32);
    }
}