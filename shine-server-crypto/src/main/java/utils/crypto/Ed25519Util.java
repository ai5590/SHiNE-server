package utils.crypto;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * ===============================================================
 *  Ed25519Util — статическая утилита для работы с подписями Ed25519
 *  на базе Bouncy Castle (bcprov). Совместимо с Java 17.
 *  ---------------------------------------------------------------
 *  Возможности:
 *   • generatePrivateKey() — приватный ключ 32 байта (seed) из SecureRandom.
 *   • generatePrivateKeyFromString(String) — приватный ключ 32 байта из строки через SHA-256.
 *   • derivePublicKey(byte[32]) — публичный ключ 32 байта из приватного.
 *   • sign(byte[], byte[32]) — подпись 64 байта.
 *   • verify(byte[], byte[64], byte[32]) — проверка подписи (true/false).
 *   • keyToBase64(byte[32]) / keyFromBase64(String) — Base64 ⇆ ключ (ровно 32 байта).
 *.
 *  Форматы:
 *   • Приватный ключ — 32-байтный seed Ed25519.
 *   • Публичный ключ — 32-байтный public key.
 *   • Подпись — 64 байта.
 *.
 *  Важно:
 *   • Здесь используется «классический» Ed25519 (подпись сырых данных).
 *     Если нужен режим Ed25519ph (prehash), делай отдельный класс.
 *.
 *  Зависимость (Gradle/Groovy):
 *   implementation 'org.bouncycastle:bcprov-jdk18on:1.78.1'
 * ===============================================================
 */
public final class Ed25519Util {

    /** Длина приватного ключа (seed) в байтах. */
    public static final int PRIVATE_KEY_LEN = 32;
    /** Длина публичного ключа в байтах. */
    public static final int PUBLIC_KEY_LEN = 32;
    /** Длина подписи в байтах. */
    public static final int SIGNATURE_LEN = 64;

    // Запрещаем инстанцирование: только статические методы
    private Ed25519Util() {}

    // ===== Надёжный генератор случайных чисел (ленивая инициализация) =====
    private static final SecureRandom SECURE_RANDOM = createSecureRandom();

    private static SecureRandom createSecureRandom() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (Exception ignore) {
            return new SecureRandom();
        }
    }

    // =====================================================================
    //                               API
    // =====================================================================

    /**
     * Сгенерировать приватный ключ (seed) Ed25519: 32 случайных байта.
     */
    public static byte[] generatePrivateKey() {
        byte[] seed = new byte[PRIVATE_KEY_LEN];
        SECURE_RANDOM.nextBytes(seed);
        return seed;
    }

    /**
     * Сгенерировать приватный ключ (seed, 32 байта) из произвольной строки:
     * строка → UTF-8 → SHA-256 → 32 байта.
     *
     * @param anyString любая строка (не null)
     * @return массив 32 байта (seed)
     */
    public static byte[] generatePrivateKeyFromString(String anyString) {
        Objects.requireNonNull(anyString, "Строка для генерации приватного ключа не должна быть null");
        byte[] input = anyString.getBytes(StandardCharsets.UTF_8);
        return HashSHA256Util.sha256(input);  // ровно 32 байта
    }

    /**
     * Получить публичный ключ (32 байта) из приватного (seed, 32 байта).
     */
    public static byte[] derivePublicKey(byte[] privateKey32) {
        requireLength(privateKey32, PRIVATE_KEY_LEN, "приватного ключа (seed)");
        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(privateKey32, 0);
        Ed25519PublicKeyParameters pub = priv.generatePublicKey();
        return pub.getEncoded(); // 32 байта
    }

    /**
     * Подписать сырые данные (без предварительного хеширования) приватным ключом Ed25519.
     *
     * @param data         данные для подписи (не null)
     * @param privateKey32 приватный ключ (seed) 32 байта
     * @return подпись длиной 64 байта
     */
    public static byte[] sign(byte[] data, byte[] privateKey32) {
        Objects.requireNonNull(data, "Данные для подписи не должны быть null");
        requireLength(privateKey32, PRIVATE_KEY_LEN, "приватного ключа (seed)");

        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(privateKey32, 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, priv);
        signer.update(data, 0, data.length);
        byte[] signature = signer.generateSignature();
        if (signature == null || signature.length != SIGNATURE_LEN) {
            throw new IllegalStateException("Ожидалась подпись длиной 64 байта.");
        }
        return signature;
    }

    /**
     * Проверить подпись Ed25519.
     *
     * @param data         исходные данные
     * @param signature64  подпись 64 байта
     * @param publicKey32  публичный ключ 32 байта
     * @return true, если подпись корректна для этих данных и ключа
     */
    public static boolean verify(byte[] data, byte[] signature64, byte[] publicKey32) {
        Objects.requireNonNull(data, "Данные для проверки подписи не должны быть null");
        requireLength(signature64, SIGNATURE_LEN, "подписи Ed25519");
        requireLength(publicKey32, PUBLIC_KEY_LEN, "публичного ключа");

        Ed25519PublicKeyParameters pub = new Ed25519PublicKeyParameters(publicKey32, 0);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, pub);
        verifier.update(data, 0, data.length);
        return verifier.verifySignature(signature64);
    }

    /**
     * Преобразовать 32-байтный ключ (приватный seed или публичный key) в Base64-строку.
     */
    public static String keyToBase64(byte[] key32) {
        requireLength(key32, 32, "ключа (ожидалось 32 байта)");
        return Base64.getEncoder().encodeToString(key32);
    }

    /**
     * Из Base64-строки получить 32-байтный ключ.
     * @throws IllegalArgumentException если после декодирования длина ≠ 32
     */
    public static byte[] keyFromBase64(String base64) {
        Objects.requireNonNull(base64, "Base64-строка не должна быть null");
        byte[] raw = Base64.getDecoder().decode(base64);
        requireLength(raw, 32, "ключа после декодирования Base64 (ожидалось 32 байта)");
        return raw;
    }

    // =====================================================================
    //                          ВСПОМОГАТЕЛЬНЫЕ
    // =====================================================================

    private static void requireLength(byte[] data, int expectedLen, String what) {
        if (data == null) {
            throw new IllegalArgumentException("Массив " + what + " не должен быть null.");
        }
        if (data.length != expectedLen) {
            throw new IllegalArgumentException(
                    "Некорректная длина " + what + ": " + data.length + " байт(а). Ожидалось: " + expectedLen + "."
            );
        }
    }

}
