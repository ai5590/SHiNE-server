package test.it.utils;

import utils.crypto.Ed25519Util;

import java.util.Base64;

public final class TestConfig {
    private TestConfig(){}

    public static final String WS_URI = "ws://localhost:7070/ws";
    public static final String TEST_LOGIN = "anya24";
    public static final String TEST_BCH_NAME = TEST_LOGIN + "0001";
    public static final int TEST_BCH_LIMIT = 1_000_000;
    public static final String TEST_CLIENT_INFO = "JavaTestClient/1.0";

    public static final byte[] LOGIN_PRIV_KEY;
    public static final String LOGIN_PUBKEY_B64;

    public static final byte[] DEVICE_PRIV_KEY;
    public static final String DEVICE_PUBKEY_B64;

    static {
        LOGIN_PRIV_KEY = Ed25519Util.generatePrivateKeyFromString("test-ed25519-login-11" + TEST_LOGIN);
        byte[] loginPub = Ed25519Util.derivePublicKey(LOGIN_PRIV_KEY);
        LOGIN_PUBKEY_B64 = Ed25519Util.keyToBase64(loginPub);

        DEVICE_PRIV_KEY = Ed25519Util.generatePrivateKeyFromString("test-ed25519-device-" + TEST_LOGIN);
        byte[] devicePub = Ed25519Util.derivePublicKey(DEVICE_PRIV_KEY);
        DEVICE_PUBKEY_B64 = Ed25519Util.keyToBase64(devicePub);
    }

    public static String fakeStoragePwd() {
        byte[] data = new byte[32];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i + 1);
        return Base64.getEncoder().encodeToString(data);
    }
}