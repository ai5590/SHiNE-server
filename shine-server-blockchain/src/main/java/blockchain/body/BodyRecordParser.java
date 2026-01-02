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
            case HeaderBody.KEY     -> new HeaderBody(bodyBytes);      // type=0, ver=1
            case TextBody.KEY       -> new TextBody(bodyBytes);        // type=1, ver=1
            case ReactionBody.KEY   -> new ReactionBody(bodyBytes);    // type=2, ver=1
            case ConnectionBody.KEY -> new ConnectionBody(bodyBytes);  // type=3, ver=1
            default -> throw new IllegalArgumentException(String.format(
                    "Unknown body type/version: type=%d ver=%d (key=0x%08X)",
                    (type & 0xFFFF), (ver & 0xFFFF), key
            ));
        };
    }
}