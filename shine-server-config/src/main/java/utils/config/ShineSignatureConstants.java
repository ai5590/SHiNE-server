package utils.config;

/**
 * ShineSignatureConstants — строковые префиксы/домены, входящие в подписываемые сообщения.
 *
 * ВАЖНО:
 *  - префикс добавляется в начало "чтобы подпись нельзя было переиспользовать" между разными типами сообщений.
 *  - менять значения после релиза нельзя, иначе старые подписи перестанут проверяться.
 */
public final class ShineSignatureConstants {

    private ShineSignatureConstants() {}

    /** Подписываемые данные параметра пользователя: prefix + login + param + time_ms + value */
    public static final String USER_PARAMETER_PREFIX = "SHiNe/UserParameter:";

    /** TAG в HeaderBody (genesis). ASCII "SHiNe". */
    public static final String BLOCKCHAIN_HEADER_TAG = "SHiNe";

    /** DOMAIN для preimage при расчёте hash32 блока. ASCII "SHiNe". */
    public static final String BLOCK_HASH_DOMAIN = "SHiNe";


    // ===================== Фиксированные размеры =====================

    /** Длина SHA-256 хэша, который хранится в блоке. */
    public static final int HASH32_LEN = 32;

    /** Длина подписи Ed25519, которая хранится в блоке. */
    public static final int SIGNATURE64_LEN = 64;

    /** Длина публичного ключа Ed25519. */
    public static final int ED25519_PUBLIC_KEY32_LEN = 32;
}