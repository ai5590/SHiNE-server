package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * TextBody_new — type=1, version=1.
 *
 * Полный bodyBytes:
 *   [2] type=1
 *   [2] version=1
 *   [payload...]
 *
 * Payload:
 *   UTF-8 bytes (N>0)
 */
public final class TextBody_new implements BodyRecord_new {

    public static final short TYPE = 1;
    public static final short VER  = 1;

    public final String message;

    /** Десериализация из полного bodyBytes (включая type/version). */
    public TextBody_new(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        if (bodyBytes.length < 5) // минимум: 4 байта type/ver + 1 байт текста
            throw new IllegalArgumentException("TextBody_new too short");

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);
        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER)
            throw new IllegalArgumentException("Not TextBody_new: type=" + type + " ver=" + ver);

        byte[] payload = new byte[bb.remaining()];
        bb.get(payload);

        // строгая проверка UTF-8
        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            this.message = decoder.decode(ByteBuffer.wrap(payload)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Text payload is not valid UTF-8", e);
        }

        if (this.message.isBlank())
            throw new IllegalArgumentException("Text message is blank");
    }

    /** Создание из строки. */
    public TextBody_new(String message) {
        Objects.requireNonNull(message, "message == null");
        if (message.isBlank())
            throw new IllegalArgumentException("message is blank");
        this.message = message;
    }

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }

    @Override
    public TextBody_new check() {
        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Text message is blank");
        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] msg = message.getBytes(StandardCharsets.UTF_8);
        if (msg.length == 0)
            throw new IllegalArgumentException("Text payload is empty");

        ByteBuffer bb = ByteBuffer.allocate(4 + msg.length).order(ByteOrder.BIG_ENDIAN);
        bb.putShort(TYPE);
        bb.putShort(VER);
        bb.put(msg);
        return bb.array();
    }
}