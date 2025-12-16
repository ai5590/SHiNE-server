package blockchain.body;

/**
 * Общий интерфейс для всех тел (body) блоков.
 *.
 * Каждый тип тела реализует:
 *   - check() — проверку корректности данных
 *   - toBytes() — опциональную сериализацию обратно в байты
 */
public interface BodyRecord {

    /** Проверить корректность содержимого. */
    BodyRecord check();

    /** (опционально) Сериализация тела обратно в байты. */
    default byte[] toBytes() {
        throw new UnsupportedOperationException("toBytes() не реализован");
    }
}
