package shine.db;

/**
 * MsgSubType — единое место для ВСЕХ subType сообщений (msg_sub_type).
 *
 * ВАЖНО:
 * - Значения должны совпадать с body-классами (TextBody/ReactionBody/ConnectionBody/UserParamBody/HeaderBody).
 * - После релиза менять числа нельзя (иначе ломается совместимость данных).
 */
public final class MsgSubType {

    private MsgSubType() {}

    /* ===================== HEADER (msg_type=0) ===================== */

    /** HeaderBody: subType всегда 0 (compat). */
    public static final short HEADER_COMPAT = 0;

    /* ===================== TEXT (msg_type=1) ===================== */

    /** POST — обычный пост в канале (в линии канала). */
    public static final short TEXT_POST = 10;

    /** EDIT_POST — редактирование исходного поста. */
    public static final short TEXT_EDIT_POST = 11;

    /** REPLY — ответ на сообщение. */
    public static final short TEXT_REPLY = 20;

    /** EDIT_REPLY — редактирование исходного ответа. */
    public static final short TEXT_EDIT_REPLY = 21;

    /* ===================== REACTION (msg_type=2) ===================== */

    /** Лайк (LIKE). */
    public static final short REACTION_LIKE = 1;
    /** Снятие лайка (UNLIKE). */
    public static final short REACTION_UNLIKE = 2;

    /* ===================== CONNECTION (msg_type=3) ===================== */
    /**
     * Совпадает с ConnectionBody:
     * SET:   FRIEND=10, CONTACT=20, FOLLOW=30
     * UNSET: UNFRIEND=11, UNCONTACT=21, UNFOLLOW=31
     */

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

    /* ===================== РЕЗЕРВ НА БУДУЩЕЕ ===================== */
    // Если позже захочешь BLOCK/UNBLOCK — лучше добавить НОВЫЕ значения,
    // не трогая 10/20/30 и 11/21/31 (например, 40/41).
    // public static final short CONNECTION_BLOCK = 40;
    // public static final short CONNECTION_UNBLOCK = 41;
}
