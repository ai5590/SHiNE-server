// =======================
// blockchain/body/BodyRecord.java   (ИЗМЕНЁННЫЙ контракт под ТЗ)
// =======================
package blockchain.body;

/**
 * BodyRecord — общий контракт для всех типов body (тела блока).
 *
 * ВАЖНО (новый формат):
 * - type/subType/version НЕ лежат в bodyBytes.
 * - type/subType/version читаются из заголовка блока (BchBlockEntry).
 *
 * Поэтому из интерфейса УБРАНЫ:
 *  - type()
 *  - subType()
 *  - version()
 *  - expectedLineIndex()
 */
public interface BodyRecord {

    /** Проверить корректность содержимого и вернуть этот объект (или кинуть исключение). */
    BodyRecord check();

    /**
     * Сериализовать тело записи в байты (ровно то, что кладётся в block.bodyBytes).
     * Важно: НЕ включает type/subType/version.
     */
    byte[] toBytes();
}