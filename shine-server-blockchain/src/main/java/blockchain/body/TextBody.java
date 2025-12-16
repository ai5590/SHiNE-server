package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * TextBody — тело записи типа 1 (простое текстовое сообщение).
 *.
 * Формат body:
 *   [N] message (UTF-8)
 *.
 * Тело полностью состоит из UTF-8-строки без каких-либо метаданных.
 */
public final class TextBody implements BodyRecord {

    public static final short TYPE = 1;

    public final String message;

    // ------------------------------------------------------------
    // Конструктор №1 — из массива байт (для парсинга)
    // ------------------------------------------------------------
    public TextBody(byte[] body) {
        Objects.requireNonNull(body, "body == null");
        if (body.length == 0)
            throw new IllegalArgumentException("Тело текстового сообщения пустое");

        // строгая проверка валидности UTF-8
        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            var chars = decoder.decode(ByteBuffer.wrap(body));
            this.message = chars.toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Тело не является корректным UTF-8", e);
        }
    }

    // ------------------------------------------------------------
    // Конструктор №2 — из строки (для создания нового сообщения)
    // ------------------------------------------------------------
    public TextBody(String message) {
        Objects.requireNonNull(message, "message == null");
        if (message.isBlank())
            throw new IllegalArgumentException("Текст сообщения не может быть пустым");
        this.message = message;
    }

    // ------------------------------------------------------------
    // Проверка и сериализация
    // ------------------------------------------------------------
    @Override
    public TextBody check() {
        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Текст сообщения не может быть пустым");
        return this;
    }

    @Override
    public byte[] toBytes() {
        return message.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "TextBody{" +
                "len=" + message.length() +
                ", msg='" + (message.length() > 60 ? message.substring(0, 57) + "..." : message) + '\'' +
                '}';
    }
}
