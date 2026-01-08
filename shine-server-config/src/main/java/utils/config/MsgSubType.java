package utils.config;

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

    /* ===================== TEXT (msg_type=1) ===================== */

    /** Новое сообщение (начало ветки). */
    public static final short TEXT_NEW = 1;

    /** Ответ на сообщение (reply). */
    public static final short TEXT_REPLY = 2;

    /** Репост (repost). */
    public static final short TEXT_REPOST = 3;

    /** Редактирование (edit). ВАЖНО: серверное значение = 10. */
    public static final short TEXT_EDIT = 10;

    /* ===================== REACTION (msg_type=2) ===================== */

    /** Лайк (LIKE). */
    public static final short REACTION_LIKE = 1;

    /* ===================== CONNECTION (msg_type=3) ===================== */

    /** Добавить в друзья. */
    public static final short CONNECTION_FRIEND = 10;

    /** Удалить из друзей. */
    public static final short CONNECTION_UNFRIEND = 11;

    /** Подписаться (follow). */
    public static final short CONNECTION_FOLLOW = 20;

    /** Отписаться (unfollow). */
    public static final short CONNECTION_UNFOLLOW = 21;

    /** Заблокировать. */
    public static final short CONNECTION_BLOCK = 30;

    /** Разблокировать. */
    public static final short CONNECTION_UNBLOCK = 31;
}