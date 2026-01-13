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
 */
public final class MsgSubType {

    private MsgSubType() {}

    /* ===================== HEADER (msg_type=0) ===================== */

    /** HeaderBody: subType всегда 0 (compat). */
    public static final short HEADER_COMPAT = 0;

    /* ===================== TEXT (msg_type=1) ===================== */

    /** Новая публикация. */
    public static final short TEXT_NEW = 1;

    /** Ответ (reply). */
    public static final short TEXT_REPLY = 2;

    /** Репост (repost). */
    public static final short TEXT_REPOST = 3;

    /** Редактирование (edit). */
    public static final short TEXT_EDIT = 10;

    /* ===================== REACTION (msg_type=2) ===================== */

    /** Лайк (LIKE). */
    public static final short REACTION_LIKE = 1;

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


}
