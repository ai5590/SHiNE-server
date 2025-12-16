package blockchain;

import blockchain.body.BodyRecord;
import blockchain.body.HeaderBody;
import blockchain.body.TextBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ============================================================================
 * BodyRecordParser — универсальный парсер тел (body) блоков .bch
 * ============================================================================
 *.
 * 🧩 Назначение:
 *   Преобразует пару (recordType, recordTypeVersion, bodyBytes)
 *   в конкретный объект, реализующий интерфейс {@link BodyRecord}.
 *.
 * 🔹 Особенность:
 *   Используется объединённый 4-байтовый код:
 *.
 *       fullCode = (recordType << 16) | (recordTypeVersion & 0xFFFF)
 *.
 *   Это позволяет различать версии одного типа блока,
 *   например: TextBody v1, TextBody v2 и т.д.
 *.
 * 🔹 Пример:
 *   BodyRecord body = BodyRecordParser.parse(block.recordType, block.recordTypeVersion, block.body);
 *.
 * ============================================================================
 */
public final class BodyRecordParser {

    private static final Logger log = LoggerFactory.getLogger(BodyRecordParser.class);

    private BodyRecordParser() {}

    /**
     * Распарсить тело блока по типу и версии записи.
     *
     * @param recordType         код типа записи (0 = Header, 1 = Text, ...)
     * @param recordTypeVersion  версия формата записи
     * @param body               массив байт тела записи
     * @return объект, реализующий BodyRecord
     */
    public static BodyRecord parse(short recordType, short recordTypeVersion, byte[] body) {
        if (body == null)
            throw new IllegalArgumentException("body == null");

        // Объединяем тип и версию в 4-байтовый ключ
        int fullCode = ((recordType & 0xFFFF) << 16) | (recordTypeVersion & 0xFFFF);

        switch (fullCode) {

            // ---------------------------------------------------------
            // TYPE 0, VERSION 1 — HeaderBody v1
            // ---------------------------------------------------------
            // Заголовок цепочки пользователя (первый блок).
            //
            // Формат body (без общих 20 байт заголовка блока):
            //  [8]  ASCII tag = "SHiNE001"
            //  [8]  blockchainId (long, BE)
            //  [4]  blockchainType (int, BE)
            //  [4]  blockchainNumber (int, BE)
            //  [1]  userLoginLength = N (unsigned byte)
            //  [N]  userLogin (UTF-8)
            //  [2]  versionUserBch (short, BE)
            //  [8]  prevUserBchId (long, BE)
            //  [32] publicKey32
            //
            // Назначение:
            //   Создаёт новую пользовательскую цепочку (блок №0).
            case (0x0000_0001):
                return new HeaderBody(body);

            // ---------------------------------------------------------
            // TYPE 1, VERSION 1 — TextBody v1
            // ---------------------------------------------------------
            // Простое текстовое сообщение UTF-8.
            //
            // Формат body (без общих 20 байт заголовка блока):
            //   [N] message (UTF-8)
            //
            // Назначение:
            //   Текстовые и системные сообщения, описания, комментарии.
            case (0x0001_0001):
                return new TextBody(body);

            // ---------------------------------------------------------
            // РЕЗЕРВ — будущие типы и версии
            // ---------------------------------------------------------
            // Пример: (0x0001_0002) → TextBody v2 (например, JSON-структура)
            //          (0x0002_0001) → FileBody v1
            //
            // case (0x0001_0002):
            //     return new TextBodyV2(body);
            //
            // case (0x0002_0001):
            //     return new FileBody(body);

            default:
                throw new IllegalArgumentException(String.format(
                        "Неизвестный тип блока: type=%d version=%d (fullCode=0x%08X)",
                        recordType, recordTypeVersion, fullCode));
        }
    }
}
