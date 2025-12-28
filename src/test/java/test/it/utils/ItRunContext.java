package test.it.utils;

import utils.crypto.Ed25519Util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Глобальный контекст интеграционного прогона (один запуск Gradle test).
 *
 * ВАЖНО:
 *  - инициализируется ровно один раз на весь процесс JVM;
 *  - хранит случайный login + blockchainName + ключи, чтобы все IT тесты работали в одной "сессии данных".
 */
public final class ItRunContext {

    private static final Object LOCK = new Object();
    private static volatile boolean inited = false;

    private static String login;
    private static String blockchainName;

    private static byte[] loginPrivKey;
    private static byte[] loginPubKey;

    private static byte[] devicePrivKey;
    private static byte[] devicePubKey;

    private ItRunContext() {}

    public static void initOnce() {
        if (inited) return;
        synchronized (LOCK) {
            if (inited) return;

            // 1) Генерим читаемый суффикс по времени + случайности, чтобы не было коллизий.
            String ts = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT));

            String rnd = randomBase32(6).toLowerCase(Locale.ROOT);

            // login должен быть валидным по твоим правилам (латиница + цифры + _)
            // Пример: it_20251226_173012_ab12cd
            login = "it_" + ts + "_" + rnd;

            // 2) blockchainName по правилу: login + 4 цифры
            blockchainName = login + TestConfig.BCH_SUFFIX_3;

            // 3) Генерация ключей ИЗ login (как ты попросила)
            //    loginKey: приватный ключ = SHA-256(login)
            loginPrivKey = Ed25519Util.generatePrivateKeyFromString(login);
            loginPubKey  = Ed25519Util.derivePublicKey(loginPrivKey);

            //    deviceKey: приватный ключ = SHA-256(login + "#device")
            String deviceSeedStr = login + "#device";
            devicePrivKey = Ed25519Util.generatePrivateKeyFromString(deviceSeedStr);
            devicePubKey  = Ed25519Util.derivePublicKey(devicePrivKey);

            inited = true;

            System.out.println(TestColors.C + "\n============================================================" + TestColors.R);
            System.out.println(TestColors.C + "IT ПРОГОН: сгенерированы случайные данные" + TestColors.R);
            System.out.println(TestColors.C + "============================================================" + TestColors.R);
            System.out.println("login           = " + login);
            System.out.println("blockchainName  = " + blockchainName);
            System.out.println("loginPubKey     = " + bytesToHexShort(loginPubKey));
            System.out.println("devicePubKey    = " + bytesToHexShort(devicePubKey));
            System.out.println(TestColors.C + "------------------------------------------------------------\n" + TestColors.R);
        }
    }

    public static String login() {
        ensureInit();
        return login;
    }

    public static String blockchainName() {
        ensureInit();
        return blockchainName;
    }

    public static byte[] loginPrivKey() {
        ensureInit();
        return loginPrivKey.clone();
    }

    public static byte[] loginPubKey() {
        ensureInit();
        return loginPubKey.clone();
    }

    public static byte[] devicePrivKey() {
        ensureInit();
        return devicePrivKey.clone();
    }

    public static byte[] devicePubKey() {
        ensureInit();
        return devicePubKey.clone();
    }

    private static void ensureInit() {
        if (!inited) {
            throw new IllegalStateException("ItRunContext ещё не инициализирован. Он должен быть вызван до тестов (через RussianSummaryListener).");
        }
    }

    private static String randomBase32(int len) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String bytesToHexShort(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(b.length, 10);
        for (int i = 0; i < n; i++) sb.append(String.format("%02x", b[i]));
        if (b.length > n) sb.append("...");
        return sb.toString();
    }
}