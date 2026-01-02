package blockchain.body;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * TextBody — type=1, ver=1.
 *
 * Формат bodyBytes (BigEndian):
 *   [2] type=1
 *   [2] ver=1
 *
 *   [2] subType (uint16): подтип текстового сообщения
 *       1 = новое сообщение (начало ветки)
 *       2 = ответ на сообщение (reply)
 *       3 = репост (repost)
 *
 *   [2] textLenBytes (uint16) — длина текста в байтах UTF-8
 *   [N] text UTF-8
 *
 *   Далее ТОЛЬКО если subType == 2 или subType == 3:
 *     [1] toBlockchainNameLen (uint8)
 *     [N] toBlockchainName UTF-8
 *     [4] toBlockGlobalNumber (int32)
 *     [32] toBlockHash32 (raw 32 bytes)
 *
 * ЛИНИЯ:
 *  - строго lineIndex=1
 *
 * Правила строгого парсинга (чтобы формат не “плыл”):
 *  - subType обязан быть 1/2/3
 *  - textLen обязан быть >0 и <=65535
 *  - text обязан быть валидным UTF-8 и не blank
 *  - для subType=NEW запрещены поля ссылки и запрещены любые “лишние байты” в хвосте
 *  - для subType=REPLY/REPOST хвост обязан быть ровно по формату и без мусора в конце
 */
public final class TextBody implements BodyRecord {

    public static final short TYPE = 1;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    // subType:
    public static final short SUB_NEW    = 1;
    public static final short SUB_REPLY  = 2;
    public static final short SUB_REPOST = 3;

    /** Подтип текстового сообщения (1/2/3). */
    public final short subType;

    /** Текст сообщения (строго валидный UTF-8, не пустой/не blank). */
    public final String message;

    // Заполняются только если subType == SUB_REPLY или SUB_REPOST
    public final String toBlockchainName;
    public final int toBlockGlobalNumber;
    public final byte[] toBlockHash32;

    /* ===================================================================== */
    /* ====================== Конструктор из байт =========================== */
    /* ===================================================================== */

    /** Десериализация из полного bodyBytes (включая type/version). */
    public TextBody(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        // минимум: type+ver (4) + subType(2) + textLen(2)
        if (bodyBytes.length < 4 + 2 + 2) {
            throw new IllegalArgumentException("TextBody too short");
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER) {
            throw new IllegalArgumentException("Not TextBody: type=" + type + " ver=" + ver);
        }

        this.subType = bb.getShort();
        if (this.subType != SUB_NEW && this.subType != SUB_REPLY && this.subType != SUB_REPOST) {
            throw new IllegalArgumentException("Bad subType: " + (this.subType & 0xFFFF));
        }

        int textLen = Short.toUnsignedInt(bb.getShort());
        if (textLen <= 0) {
            throw new IllegalArgumentException("Text payload is empty");
        }
        if (bb.remaining() < textLen) {
            throw new IllegalArgumentException("Text payload too short (len=" + textLen + ")");
        }

        byte[] textBytes = new byte[textLen];
        bb.get(textBytes);

        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            this.message = decoder.decode(ByteBuffer.wrap(textBytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Text payload is not valid UTF-8", e);
        }

        if (this.message.isBlank()) {
            throw new IllegalArgumentException("Text message is blank");
        }

        // Поля ссылки — только для reply/repost
        if (this.subType == SUB_REPLY || this.subType == SUB_REPOST) {

            if (bb.remaining() < 1) {
                throw new IllegalArgumentException("Missing toBlockchainNameLen");
            }

            int nameLen = Byte.toUnsignedInt(bb.get());
            if (nameLen <= 0) throw new IllegalArgumentException("toBlockchainNameLen is 0");
            if (bb.remaining() < nameLen + 4 + 32) {
                throw new IllegalArgumentException("Reply/Repost payload too short");
            }

            byte[] nameBytes = new byte[nameLen];
            bb.get(nameBytes);
            this.toBlockchainName = new String(nameBytes, StandardCharsets.UTF_8);

            this.toBlockGlobalNumber = bb.getInt();

            this.toBlockHash32 = new byte[32];
            bb.get(this.toBlockHash32);

            // Запрет мусора в конце
            if (bb.remaining() != 0) {
                throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
            }

        } else {
            // SUB_NEW
            this.toBlockchainName = null;
            this.toBlockGlobalNumber = 0;
            this.toBlockHash32 = null;

            // если кто-то подсунул хвост — лучше упасть, чтобы формат не “плыл”
            if (bb.remaining() != 0) {
                throw new IllegalArgumentException("Unexpected tail for subType=NEW, remaining=" + bb.remaining());
            }
        }
    }

    /* ===================================================================== */
    /* ====================== Конструкторы “для тестов” ====================== */
    /* ===================================================================== */

    /**
     * Удобный конструктор для тестов/сборки простого сообщения:
     *   new TextBody(text) == new TextBody(SUB_NEW, text)
     */
    public TextBody(String message) {
        this(SUB_NEW, message);
    }

    /** Сообщение subType=NEW (1). */
    public TextBody(short subType, String message) {
        Objects.requireNonNull(message, "message == null");

        if (subType != SUB_NEW) {
            throw new IllegalArgumentException("This constructor is only for SUB_NEW");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message is blank");
        }

        this.subType = subType;
        this.message = message;

        this.toBlockchainName = null;
        this.toBlockGlobalNumber = 0;
        this.toBlockHash32 = null;
    }

    /** Сообщение subType=REPLY (2) или subType=REPOST (3) со ссылкой на блок. */
    public TextBody(short subType,
                    String message,
                    String toBlockchainName,
                    int toBlockGlobalNumber,
                    byte[] toBlockHash32) {

        Objects.requireNonNull(message, "message == null");
        Objects.requireNonNull(toBlockchainName, "toBlockchainName == null");
        Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");

        if (subType != SUB_REPLY && subType != SUB_REPOST) {
            throw new IllegalArgumentException("subType must be SUB_REPLY or SUB_REPOST for this constructor");
        }
        if (message.isBlank()) throw new IllegalArgumentException("message is blank");
        if (toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
        if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

        this.subType = subType;
        this.message = message;
        this.toBlockchainName = toBlockchainName;
        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);
    }

    /* ===================================================================== */
    /* ====================== BodyRecord контракт =========================== */
    /* ===================================================================== */

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }

    /** ✅ ВАЖНО: теперь BodyRecord требует subType() */
    @Override public short subType() { return subType; }

    @Override
    public short expectedLineIndex() {
        return 1;
    }

    @Override
    public TextBody check() {
        if (subType != SUB_NEW && subType != SUB_REPLY && subType != SUB_REPOST) {
            throw new IllegalArgumentException("Bad subType: " + (subType & 0xFFFF));
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Text message is blank");
        }

        if (subType == SUB_REPLY || subType == SUB_REPOST) {
            if (toBlockchainName == null || toBlockchainName.isBlank())
                throw new IllegalArgumentException("toBlockchainName is blank");
            if (toBlockGlobalNumber < 0)
                throw new IllegalArgumentException("toBlockGlobalNumber < 0");
            if (toBlockHash32 == null || toBlockHash32.length != 32)
                throw new IllegalArgumentException("toBlockHash32 invalid");
        } else {
            // SUB_NEW
            if (toBlockchainName != null) throw new IllegalArgumentException("toBlockchainName must be null for SUB_NEW");
            if (toBlockHash32 != null) throw new IllegalArgumentException("toBlockHash32 must be null for SUB_NEW");
        }

        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] msgUtf8 = message.getBytes(StandardCharsets.UTF_8);
        if (msgUtf8.length == 0) {
            throw new IllegalArgumentException("Text payload is empty");
        }
        if (msgUtf8.length > 65535) {
            throw new IllegalArgumentException("Text too long (>65535 bytes)");
        }

        // base: type+ver + subType + textLen + textBytes
        int cap = 4 + 2 + 2 + msgUtf8.length;

        byte[] nameBytes = null;

        if (subType == SUB_REPLY || subType == SUB_REPOST) {
            nameBytes = toBlockchainName.getBytes(StandardCharsets.UTF_8);
            if (nameBytes.length == 0 || nameBytes.length > 255) {
                throw new IllegalArgumentException("toBlockchainName utf8 len must be 1..255");
            }
            if (toBlockHash32 == null || toBlockHash32.length != 32) {
                throw new IllegalArgumentException("toBlockHash32 != 32");
            }

            cap += 1 + nameBytes.length + 4 + 32;

        } else {
            // SUB_NEW — ссылка запрещена
            if (toBlockchainName != null || toBlockHash32 != null) {
                throw new IllegalArgumentException("SUB_NEW must not contain reply/repost fields");
            }
        }

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putShort(TYPE);
        bb.putShort(VER);

        bb.putShort(subType);

        bb.putShort((short) msgUtf8.length);
        bb.put(msgUtf8);

        if (subType == SUB_REPLY || subType == SUB_REPOST) {
            bb.put((byte) nameBytes.length);
            bb.put(nameBytes);
            bb.putInt(toBlockGlobalNumber);
            bb.put(toBlockHash32);
        }

        return bb.array();
    }

    @Override
    public String toString() {
        String st = switch (subType) {
            case SUB_NEW -> "NEW (1)";
            case SUB_REPLY -> "REPLY (2)";
            case SUB_REPOST -> "REPOST (3)";
            default -> "UNKNOWN";
        };

        if (subType == SUB_REPLY || subType == SUB_REPOST) {
            return """
                    TextBody {
                      тип записи        : TEXT (type=1, ver=1)
                      ожидаемая линия   : 1
                      subType           : %s
                      длина сообщения   : %d байт
                      текст сообщения   : "%s"
                      ссылка на блок    : "%s" #%d
                      hash цели (hex)   : %s
                    }
                    """.formatted(
                    st,
                    message.getBytes(StandardCharsets.UTF_8).length,
                    message,
                    toBlockchainName,
                    toBlockGlobalNumber,
                    toBlockHashHex()
            );
        }

        return """
                TextBody {
                  тип записи        : TEXT (type=1, ver=1)
                  ожидаемая линия   : 1
                  subType           : %s
                  длина сообщения   : %d байт
                  текст сообщения   : "%s"
                }
                """.formatted(
                st,
                message.getBytes(StandardCharsets.UTF_8).length,
                message
        );
    }

    public String toBlockHashHex() {
        if (toBlockHash32 == null) return "null";
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[64];
        for (int i = 0; i < 32; i++) {
            int v = toBlockHash32[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}