package test.it.utils;

import java.util.Base64;

/**
 * Конфиг для IT тестов.
 *
 * ВАЖНО:
 *  - login/blockchainName/ключи берём из ItRunContext (случайные на каждый прогон).
 */
public final class TestConfig {

    private TestConfig() {}

    // Твой WS URI
    public static final String WS_URI = "ws://localhost:7070/ws";

    // Суффикс блокчейна по твоему правилу: login + 3 цифры
    public static final String BCH_SUFFIX_3 = "001";

    // Лимит блокчейна для AddUser
    public static final long TEST_BCH_LIMIT = 50_000_000L;

    // Любая строка клиента (для логов)
    public static final String TEST_CLIENT_INFO = "it-tests";

    public static String TEST_LOGIN() {
        return ItRunContext.login();
    }

    public static String TEST_BCH_NAME() {
        return ItRunContext.blockchainName();
    }

    public static byte[] LOGIN_PRIV_KEY() {
        return ItRunContext.loginPrivKey();
    }

    public static byte[] LOGIN_PUB_KEY() {
        return ItRunContext.loginPubKey();
    }

    public static byte[] DEVICE_PRIV_KEY() {
        return ItRunContext.devicePrivKey();
    }

    public static byte[] DEVICE_PUB_KEY() {
        return ItRunContext.devicePubKey();
    }

    public static String LOGIN_PUBKEY_B64() {
        return Base64.getEncoder().encodeToString(LOGIN_PUB_KEY());
    }

    public static String DEVICE_PUBKEY_B64() {
        return Base64.getEncoder().encodeToString(DEVICE_PUB_KEY());
    }

    /** Псевдо-пароль хранилища — достаточно для тестов. */
    public static String fakeStoragePwd() {
        return "pwd-" + System.nanoTime();
    }
}
