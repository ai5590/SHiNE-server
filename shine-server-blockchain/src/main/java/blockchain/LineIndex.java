package blockchain;

/**
 * LineIndex — канонические номера линий блокчейна.
 *
 * Линия = независимая последовательность блоков внутри одного блокчейна.
 */
public final class LineIndex {

    private LineIndex() {}

    public static final short HEADER      = 0; // genesis / идентификация
    public static final short TEXT        = 1; // сообщения                                  да надо
    public static final short REACTION    = 2; // реакции                          не надо
    public static final short CONNECTION  = 3; // связи (friend/contact/follow)              да надо
    public static final short USER_PARAM  = 4; // параметры профиля                          да надо
}