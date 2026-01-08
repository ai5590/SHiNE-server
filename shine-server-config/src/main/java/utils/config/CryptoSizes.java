package utils.config;

/**
 * CryptoSizes — единые размеры крипто-полей и ключей.
 * Никаких "32/64" по коду руками — только отсюда.
 */
public final class CryptoSizes {

    private CryptoSizes() {}

    /** Длина SHA-256 хэша, который хранится в блоке. */
    public static final int HASH32_LEN = 32;

    /** Длина подписи Ed25519, которая хранится в блоке. */
    public static final int SIGNATURE64_LEN = 64;

    /** Длина публичного ключа Ed25519. */
    public static final int ED25519_PUBLIC_KEY32_LEN = 32;
}