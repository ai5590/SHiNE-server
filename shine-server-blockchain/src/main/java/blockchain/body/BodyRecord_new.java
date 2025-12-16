package blockchain.body;

/**
 * BodyRecord_new — общий контракт для всех типов body (тела блока).
 *
 * Идея:
 *  - На каждый тип body (Header, Text, File, ...) — отдельный класс.
 *  - Десериализация из байтов делается КОНСТРУКТОРОМ:
 *      new XxxBody_new(byte[] bodyBytes)
 *    (конструктор обязан распарсить байты или кинуть IllegalArgumentException).
 *
 *  - Валидация делается методом check().
 *    check() должен:
 *      - вернуть this, если всё корректно
 *      - кинуть IllegalArgumentException, если данные некорректны
 *
 *  - Сериализация обратно в байты делается методом toBytes().
 *
 *  - type() и version() — это идентификаторы формата body.
 *    Они должны быть константами для класса (например TYPE=1, VERSION=1).
 */
public interface BodyRecord_new {

    /** Код типа записи (совпадает с recordType в BchBlockEntry). */
    short type();

    /** Версия формата записи (совпадает с recordTypeVersion в BchBlockEntry). */
    short version();

    /** Проверить корректность содержимого и вернуть этот объект (или кинуть исключение). */
    BodyRecord_new check();

    /**
     * Сериализовать тело записи в байты (ровно то, что кладётся в block.body).
     * Важно: НЕ включает общий заголовок блока (recordNumber/timestamp/type/version).
     */
    byte[] toBytes();
}
