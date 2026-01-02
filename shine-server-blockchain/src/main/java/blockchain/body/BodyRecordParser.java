package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class BodyRecordParser {

    private BodyRecordParser() {}

    public static BodyRecord parse(byte[] bodyBytes) {
        if (bodyBytes == null) throw new IllegalArgumentException("bodyBytes == null");
        if (bodyBytes.length < 4) throw new IllegalArgumentException("bodyBytes too short (<4)");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);
        short type = bb.getShort();
        short ver  = bb.getShort();

        int key = ((type & 0xFFFF) << 16) | (ver & 0xFFFF);

        return switch (key) {
            case 0x0000_0001 -> new HeaderBody(bodyBytes);    // type=0, ver=1       // заглавие блокчейна
            case 0x0001_0001 -> new TextBody(bodyBytes);      // type=1, ver=1       // текстовое сообщение
            case 0x0002_0001 -> new ReactionBody(bodyBytes);  // type=2, ver=1       // реакция
            case 0x0003_0001 -> new LinkBody(bodyBytes);      // type=3, ver=1       // связь
            default -> throw new IllegalArgumentException(String.format(
                    "Unknown body type/version: type=%d ver=%d (key=0x%08X)",
                    (type & 0xFFFF), (ver & 0xFFFF), key
            ));
        };
    }
}