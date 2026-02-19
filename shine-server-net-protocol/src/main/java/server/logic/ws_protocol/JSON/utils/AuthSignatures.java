////package server.logic.ws_protocol.JSON.utils;
//
//import shine.db.entities.SolanaUserEntry;
//import utils.crypto.Ed25519Util;
//
//import java.nio.charset.StandardCharsets;
//import java.util.Base64;
//
//public final class AuthSignatures {
//
//    private AuthSignatures() {}
//
//    /** preimage для CreateAuthSession(v2): "AUTH_CREATE_SESSION:login:timeMs:authNonce" */
//    public static byte[] preimageCreateAuthSession(String login, long timeMs, String authNonce) {
//        String preimageStr = "AUTH_CREATE_SESSION:" + login + ":" + timeMs + ":" + authNonce;
//        return preimageStr.getBytes(StandardCharsets.UTF_8);
//    }
//
//    /** Декод base64 / base64url (если надо — подстрой под твой decodeBase64Any) */
//    public static byte[] decodeBase64Any(String s) throws IllegalArgumentException {
//        if (s == null) throw new IllegalArgumentException("base64 is null");
//        String x = s.trim();
//        if (x.isEmpty()) throw new IllegalArgumentException("base64 is empty");
//
//        try {
//            return Base64.getDecoder().decode(x);
//        } catch (IllegalArgumentException e1) {
//            // пробуем base64url без паддинга
//            return Base64.getUrlDecoder().decode(x);
//        }
//    }
//
//    /**
//     * Проверка подписи CreateAuthSession(v2) по deviceKey пользователя.
//     * Подпись проверяется над preimageCreateAuthSession(...).
//     */
//    public static boolean verifyCreateAuthSessionSignature(
//            SolanaUserEntry user,
//            String login,
//            String authNonce,
//            long timeMs,
//            String signatureB64
//    ) throws IllegalArgumentException {
//
//        // user.getDeviceKey() — base64 публичного ключа (32 байта)
//        byte[] publicKey32 = decodeBase64Any(user.getDeviceKey());
//        byte[] signature64 = decodeBase64Any(signatureB64);
//
//        byte[] preimage = preimageCreateAuthSession(login, timeMs, authNonce);
//        return Ed25519Util.verify(preimage, signature64, publicKey32);
//    }
//}