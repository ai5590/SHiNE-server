package utils.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class CryptoSelfTest {

    private CryptoSelfTest() {}

    /**
     * Простой запуск: убедиться, что всё собрано и работает.
     * Выводит ключи в Base64, знак/проверка подписи — OK/FAIL.
     */
    public static void main(String[] args) {
        System.out.println("=== Ed25519 self-check ===");

        // 1) Генерация ключей
        byte[] priv = Ed25519Util.generatePrivateKey();
        byte[] pub  = Ed25519Util.derivePublicKey(priv);

        // 2) Конвертация в/из Base64 (чисто для демонстрации)
        String privB64 = Ed25519Util.keyToBase64(priv);
        String pubB64  = Ed25519Util.keyToBase64(pub);
        System.out.println("Private (seed) Base64: " + privB64);
        System.out.println("Public  Base64       : " + pubB64);

        byte[] priv2 = Ed25519Util.keyFromBase64(privB64);
        byte[] pub2  = Ed25519Util.keyFromBase64(pubB64);
        if (!Arrays.equals(priv, priv2) || !Arrays.equals(pub, pub2)) {
            throw new IllegalStateException("Base64 ⇆ bytes дала несовпадение (не должно случаться).");
        }

        // 3) Подпись и проверка
        byte[] data = "Привет, мир Ed25519!".getBytes(StandardCharsets.UTF_8);
        byte[] sig  = Ed25519Util.sign(data, priv);

        boolean ok = Ed25519Util.verify(data, sig, pub);
        System.out.println("Verify OK? " + ok);

        // 4) Негативный тест: портим данные
        byte[] bad = "Привет, мир Ed25519?".getBytes(StandardCharsets.UTF_8);
        boolean shouldFail = Ed25519Util.verify(bad, sig, pub);
        System.out.println("Verify on changed data (should be false): " + shouldFail);

        if (!ok || shouldFail) {
            throw new IllegalStateException("Self-test failed.");
        }
        System.out.println("Self-test passed ✅");
    }
}
