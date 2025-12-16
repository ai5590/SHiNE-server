package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * BodyRecordParser_new — общий фабричный парсер body для нового формата.
 *
 * Правило совместимости (строгое):
 * - если (type, version) неизвестны → кидаем IllegalArgumentException
 */
public final class BodyRecordParser_new {

    private BodyRecordParser_new() {}

    public static BodyRecord_new parse(byte[] bodyBytes) {
        if (bodyBytes == null) throw new IllegalArgumentException("bodyBytes == null");
        if (bodyBytes.length < 4) throw new IllegalArgumentException("bodyBytes too short (<4)");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);
        short type = bb.getShort();
        short ver  = bb.getShort();

        // Строгое сопоставление type+version → класс
        int key = ((type & 0xFFFF) << 16) | (ver & 0xFFFF);

        return switch (key) {
            case 0x0000_0001 -> new HeaderBody_new(bodyBytes); // type=0, ver=1
            case 0x0001_0001 -> new TextBody_new(bodyBytes);   // type=1, ver=1
            default -> throw new IllegalArgumentException(String.format(
                    "Unknown body type/version: type=%d ver=%d (key=0x%08X)",
                    (type & 0xFFFF), (ver & 0xFFFF), key
            ));
        };
    }
}