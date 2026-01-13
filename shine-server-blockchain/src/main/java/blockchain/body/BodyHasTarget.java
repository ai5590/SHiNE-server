package blockchain.body;

/**
 * BodyHasTarget — дополнительный интерфейс для body, которые "ссылаются" на цель (to-поля).
 *
 * Идея:
 *  - Не все body имеют "to".
 *  - Но для индексации и удобства запросов в БД мы хотим единообразно доставать:
 *      toLogin, toBchName, toBlockGlobalNumber, toBlockHashe
 *
 * Важно:
 *  - Все методы могут возвращать null.
 */
public interface BodyHasTarget {

    /** login цели (nullable). */
    String toLogin();

    /** blockchainName цели (nullable). */
    String toBchName();

    /** globalNumber цели (nullable). */
    Integer toBlockGlobalNumber();

    /** hash целевого блока (обычно 32 байта). Может быть null, если ссылки нет. */
    byte[] toBlockHasheBytes();
}