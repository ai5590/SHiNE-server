package blockchain;

/**
 * MsgSubType — единое место для ВСЕХ subType сообщений (msg_sub_type).
 *
 * Правило:
 *  - НИКАКИХ "магических чисел" subType по проекту.
 *  - В тестах, в body-классах и в SQL-триггерах используем только эти константы.
 *
 * Важно:
 *  - Значения менять после релиза нельзя (иначе сломается совместимость).
 *
 * =========================================================================
 * Про EDIT-типы (важные правила, чтобы не было “двойных правок”):
 *
 * 1) EDIT разрешён ТОЛЬКО автору (в своём блокчейне).
 *    Никаких “я отредачу чужое” — нельзя.
 *
 * 2) EDIT всегда ссылается ТОЛЬКО на ОРИГИНАЛ:
 *    - EDIT_POST -> на исходный POST
 *    - EDIT_REPLY -> на исходный REPLY
 *    НЕЛЬЗЯ ссылаться на предыдущий EDIT (цепочка edit-ов запрещена).
 *
 * 3) REPLY может ссылаться на блоки в чужих линиях / чужих каналах,
 *    и существование цели на уровне check() не проверяется
 *    (check() БД не видит). Если цели нет — “никто не увидит” и ок.
 * =========================================================================
 */
public final class MsgSubType {

    private MsgSubType() {}

    /* ===================== HEADER (msg_type=0) ===================== */

    /** HeaderBody: subType всегда 0 (compat). */
    public static final short HEADER_COMPAT = 0;
    public static final short TECH_CREATE_CHANNEL = 1;

    /* ===================== TEXT (msg_type=1) ===================== */

    /**
     * POST — обычный пост в канале (в линии канала).
     * Имеет hasLine (prevLineNumber/prevLineHash32/thisLineNumber).
     */
    public static final short TEXT_POST = 10;

    /**
     * EDIT_POST — редактирование ПОСТА.
     * Имеет hasLine (принадлежит линии канала)
     * И имеет target на ОРИГИНАЛЬНЫЙ POST (без toBlockchainName).
     */
    public static final short TEXT_EDIT_POST = 11;

    /**
     * REPLY — ответ на сообщение.
     * НЕ в линии. Имеет target (toBlockchainName + blockNumber + hash32).
     * Может указывать на чужой блокчейн/чужую линию/чужой канал.
     */
    public static final short TEXT_REPLY = 20;

    /**
     * EDIT_REPLY — редактирование ОТВЕТА.
     * НЕ в линии. Имеет target на ОРИГИНАЛЬНЫЙ REPLY (без toBlockchainName).
     */
    public static final short TEXT_EDIT_REPLY = 21;

    /* ===================== REACTION (msg_type=2) ===================== */

    /** Лайк (LIKE). */
    public static final short REACTION_LIKE = 1;

    /* ===================== CONNECTION (msg_type=3) ===================== */

    /** Добавить в друзья. */
    public static final short CONNECTION_FRIEND = 10;
    /** Удалить из друзей. */
    public static final short CONNECTION_UNFRIEND = 11;

    /** Добавить в контакты. */
    public static final short CONNECTION_CONTACT = 20;
    /** Удалить из контактов. */
    public static final short CONNECTION_UNCONTACT = 21;

    /** Подписаться (follow). */
    public static final short CONNECTION_FOLLOW = 30;
    /** Отписаться (unfollow). */
    public static final short CONNECTION_UNFOLLOW = 31;

    /* ===================== USER_PARAM (msg_type=4) ===================== */

    /** Параметр профиля key/value (обе строки). */
    public static final short USER_PARAM_TEXT_TEXT = 1;
}