package test.it.utils;

import utils.crypto.Ed25519Util;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TestConfig — конфиг IT тестов:
 *  - 3 пользователя (TestUser1/2/3)
 *  - ключи по login через map (device/solana/blockchain/session)
 *  - blockchainName = login + "-" + "001"
 *
 * Важно:
 *  - privateKey = Ed25519Util.generatePrivateKeyFromString(seed) (sha256(seed) => 32 bytes)
 *  - publicKey  = Ed25519Util.derivePublicKey(privateKey)
 *  - device/solana/blockchain ключи пока одинаковые (seed = login)
 *  - session ключ отдельный (seed = "session:" + login) — чтобы SessionLogin был честнее.
 */
public final class TestConfig {

    private TestConfig() {}

    public static final String WS_URI_LOCAL = "ws://localhost:7070/ws";
    public static final String WS_URI_Server = "wss://shineup.me/ws";

    // по умолчанию LOCAL, но можно переопределить: -Dit.wsUri=...
    public static final String WS_URI = System.getProperty("it.wsUri", WS_URI_LOCAL);

    public static final long TEST_BCH_LIMIT = 50_000_000L;
    public static final String TEST_CLIENT_INFO = "it-tests";

    public static boolean DEBUG() {
        return Boolean.parseBoolean(System.getProperty("it.debug", "true"));
    }

    // 3 users
    public static final String DEFAULT_LOGIN1 = "TestUser1";
    public static final String DEFAULT_LOGIN2 = "TestUser2";
    public static final String DEFAULT_LOGIN3 = "TestUser3";
    public static final String DEFAULT_BCH_SUFFIX_3 = "001";

    public static String LOGIN()  { return System.getProperty("it.login1", DEFAULT_LOGIN1); }
    public static String LOGIN2() { return System.getProperty("it.login2", DEFAULT_LOGIN2); }
    public static String LOGIN3() { return System.getProperty("it.login3", DEFAULT_LOGIN3); }

    public static String BCH_SUFFIX_3() {
        return System.getProperty("it.bchSuffix", DEFAULT_BCH_SUFFIX_3);
    }

    public static String getBlockchainName(String login) {
        if (login == null) throw new IllegalArgumentException("login is null");
        return login + "-" + BCH_SUFFIX_3();
    }

    // ============ key maps ============
    private static final Map<String, byte[]> devicePriv = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> devicePub  = new ConcurrentHashMap<>();

    private static final Map<String, byte[]> solanaPriv = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> solanaPub  = new ConcurrentHashMap<>();

    private static final Map<String, byte[]> bchPriv = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> bchPub  = new ConcurrentHashMap<>();

    // NEW: session keys (для SessionLogin v2)
    private static final Map<String, byte[]> sessionPriv = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> sessionPub  = new ConcurrentHashMap<>();

    static {
        initUserKeys(LOGIN());
        initUserKeys(LOGIN2());
        initUserKeys(LOGIN3());
    }

    private static void initUserKeys(String login) {
        // seed = login
        byte[] priv = Ed25519Util.generatePrivateKeyFromString(login); // sha256(login) => 32 bytes
        byte[] pub  = Ed25519Util.derivePublicKey(priv);

        // пока одинаковые
        devicePriv.put(login, priv);
        devicePub.put(login, pub);

        solanaPriv.put(login, priv);
        solanaPub.put(login, pub);

        bchPriv.put(login, priv);
        bchPub.put(login, pub);

        // session seed = "session:" + login (отдельно!)
        byte[] sPriv = Ed25519Util.generatePrivateKeyFromString("session:" + login);
        byte[] sPub  = Ed25519Util.derivePublicKey(sPriv);

        sessionPriv.put(login, sPriv);
        sessionPub.put(login, sPub);
    }

    // ============ requested getters (with your names) ============

    public static byte[] getDevicePrivatKey(String login) { return cloneOrThrow(devicePriv.get(login), "devicePriv", login); }
    public static byte[] getDevicePublicKey(String login) { return cloneOrThrow(devicePub.get(login), "devicePub", login); }

    public static byte[] getSolanaPrivatKey(String login) { return cloneOrThrow(solanaPriv.get(login), "solanaPriv", login); }
    public static byte[] getSolanaPublicKey(String login) { return cloneOrThrow(solanaPub.get(login), "solanaPub", login); }

    public static byte[] getBlockchainPrivatKey(String login) { return cloneOrThrow(bchPriv.get(login), "bchPriv", login); }
    public static byte[] getBlockchainPublicKey(String login) { return cloneOrThrow(bchPub.get(login), "bchPub", login); }

    // NEW: session getters
    public static byte[] getSessionPrivatKey(String login) { return cloneOrThrow(sessionPriv.get(login), "sessionPriv", login); }
    public static byte[] getSessionPublicKey(String login) { return cloneOrThrow(sessionPub.get(login), "sessionPub", login); }

    // ============ base64 helpers ============
    public static String devicePublicKeyB64(String login) { return Base64.getEncoder().encodeToString(getDevicePublicKey(login)); }
    public static String solanaPublicKeyB64(String login) { return Base64.getEncoder().encodeToString(getSolanaPublicKey(login)); }
    public static String blockchainPublicKeyB64(String login) { return Base64.getEncoder().encodeToString(getBlockchainPublicKey(login)); }

    // NEW: session pub b64 helper
    public static String sessionPublicKeyB64(String login) { return Base64.getEncoder().encodeToString(getSessionPublicKey(login)); }

    // ============ backward-compatible helpers for "user1" ============
    public static String BCH_NAME() { return getBlockchainName(LOGIN()); }
    public static String BCH_NAME2() { return getBlockchainName(LOGIN2()); }
    public static String BCH_NAME3() { return getBlockchainName(LOGIN3()); }

    /** solanaKey для AddUser: публичный ключ Solana-пользователя */
    public static String SOLANA_PUBKEY_B64() { return solanaPublicKeyB64(LOGIN()); }
    public static String SOLANA2_PUBKEY_B64() { return solanaPublicKeyB64(LOGIN2()); }
    public static String SOLANA3_PUBKEY_B64() { return solanaPublicKeyB64(LOGIN3()); }

    /** blockchainKey для AddUser: публичный ключ блокчейна */
    public static String BLOCKCHAIN_PUBKEY_B64() { return blockchainPublicKeyB64(LOGIN()); }
    public static String BLOCKCHAIN2_PUBKEY_B64() { return blockchainPublicKeyB64(LOGIN2()); }
    public static String BLOCKCHAIN3_PUBKEY_B64() { return blockchainPublicKeyB64(LOGIN3()); }

    public static String DEVICE_PUBKEY_B64() { return devicePublicKeyB64(LOGIN()); }
    public static String DEVICE2_PUBKEY_B64() { return devicePublicKeyB64(LOGIN2()); }
    public static String DEVICE3_PUBKEY_B64() { return devicePublicKeyB64(LOGIN3()); }

    // NEW: session pub b64 compat
    public static String SESSION_PUBKEY_B64() { return sessionPublicKeyB64(LOGIN()); }
    public static String SESSION2_PUBKEY_B64() { return sessionPublicKeyB64(LOGIN2()); }
    public static String SESSION3_PUBKEY_B64() { return sessionPublicKeyB64(LOGIN3()); }

    // ============ misc ============
    public static String fakeStoragePwd() {
        return "pwd-" + System.nanoTime();
    }

    private static byte[] cloneOrThrow(byte[] v, String mapName, String login) {
        if (login == null) throw new IllegalArgumentException("login is null");
        if (v == null) throw new IllegalStateException("No key in " + mapName + " for login=" + login);
        return v.clone();
    }
}