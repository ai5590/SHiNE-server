package utils.config;

/**
 * ShineSignatureConstants — строковые префиксы, входящие в подписываемые сообщения.
 *
 * ВАЖНО:
 *  - префикс добавляется в начало "чтобы подпись нельзя было переиспользовать" между разными типами сообщений.
 *  - менять префиксы после релиза нельзя, иначе старые подписи перестанут проверяться.
 */
public final class ShineSignatureConstants {

    private ShineSignatureConstants() {}

    /** Подписываемые данные параметра пользователя: prefix + login + param + time_ms + value */
    public static final String USER_PARAMETER_PREFIX = "SHiNe/UserParameter:";
}