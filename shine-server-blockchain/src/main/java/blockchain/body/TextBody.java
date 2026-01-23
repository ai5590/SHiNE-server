package blockchain.body;

import blockchain.MsgSubType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * TextBody — type=1, ver=1 (в заголовке блока).
 *
 * subType (в заголовке блока):
 *   10 = POST
 *   11 = EDIT_POST
 *   20 = REPLY
 *   21 = EDIT_REPLY
 *
 * =========================================================================
 * КОНЦЕПЦИЯ ЛИНИЙ ДЛЯ ТЕКСТОВЫХ СООБЩЕНИЙ:
 *
 * POST и EDIT_POST принадлежат ЛИНИИ КАНАЛА и имеют hasLine.
 * В новом формате добавлен lineCode:
 *   lineCode = 0 для канала "0"
 *   lineCode = blockNumber "заглавия линии/канала" (например CREATE_CHANNEL)
 *
 * REPLY и EDIT_REPLY НЕ имеют линии (нет hasLine в байтах).
 *
 * =========================================================================
 * ФОРМАТЫ bodyBytes (BigEndian):
 *
 * 1) POST (subType=10):
 *   [4]  lineCode
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *   [2]  textLenBytes (uint16)
 *   [N]  text UTF-8
 *
 * 2) EDIT_POST (subType=11):
 *   [4]  lineCode
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *
 *   hasTarget (на ОРИГИНАЛЬНЫЙ POST, toBchName НЕ хранить):
 *     [4]  toBlockGlobalNumber
 *     [32] toBlockHash32
 *
 *   [2]  textLenBytes (uint16)
 *   [N]  text UTF-8
 *
 * 3) REPLY (subType=20) — НЕ в линии:
 *   hasTarget:
 *     [1]  toBlockchainNameLen (uint8)
 *     [N]  toBlockchainName UTF-8
 *     [4]  toBlockGlobalNumber
 *     [32] toBlockHash32
 *
 *   [2]  textLenBytes (uint16)
 *   [M]  text UTF-8
 *
 * 4) EDIT_REPLY (subType=21) — НЕ в линии:
 *   hasTarget (на ОРИГИНАЛЬНЫЙ REPLY, toBchName НЕ хранить):
 *     [4]  toBlockGlobalNumber
 *     [32] toBlockHash32
 *
 *   [2]  textLenBytes (uint16)
 *   [N]  text UTF-8
 */
public final class TextBody implements BodyRecord, BodyHasTarget, BodyHasLine {

    public static final short TYPE = 1;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    public final short subType;   // из header
    public final short version;   // из header

    // ===== line fields (только для POST/EDIT_POST) =====
    // Для REPLY/EDIT_REPLY эти поля НЕ сериализуются; значения держим как "пустые".
    public final int lineCode;         // только для line-message; иначе -1
    public final int prevLineNumber;
    public final byte[] prevLineHash32; // 32 or null
    public final int thisLineNumber;

    // ===== message text =====
    public final String message;

    // ===== target fields =====
    // REPLY: toBlockchainName + globalNumber + hash32
    // EDIT_POST / EDIT_REPLY: только globalNumber + hash32 (без toBlockchainName)
    public final String toBlockchainName;     // nullable
    public final Integer toBlockGlobalNumber; // nullable
    public final byte[] toBlockHash32;        // nullable (но если target есть -> 32)

    /* ===================================================================== */
    /* ====================== Конструктор из байт ========================== */
    /* ===================================================================== */

    public TextBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("TextBody version must be 1, got=" + (this.version & 0xFFFF));
        }
        if (!isValidSubType(this.subType)) {
            throw new IllegalArgumentException("Bad Text subType: " + (this.subType & 0xFFFF));
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        int st = this.subType & 0xFFFF;

        if (st == (MsgSubType.TEXT_POST & 0xFFFF)) {
            // POST: hasLine(lineCode+line) + text
            ensureMin(bb, (4 + 4 + 32 + 4) + 2, "POST too short");

            this.lineCode = bb.getInt();
            this.prevLineNumber = bb.getInt();
            this.prevLineHash32 = new byte[32];
            bb.get(this.prevLineHash32);
            this.thisLineNumber = bb.getInt();

            this.message = readStrictUtf8Len16(bb, "POST text");

            this.toBlockchainName = null;
            this.toBlockGlobalNumber = null;
            this.toBlockHash32 = null;

            ensureNoTail(bb, "POST");

        } else if (st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            // EDIT_POST: hasLine(lineCode+line) + target(no bch) + text
            ensureMin(bb, (4 + 4 + 32 + 4) + (4 + 32) + 2, "EDIT_POST too short");

            this.lineCode = bb.getInt();
            this.prevLineNumber = bb.getInt();
            this.prevLineHash32 = new byte[32];
            bb.get(this.prevLineHash32);
            this.thisLineNumber = bb.getInt();

            int tgtNum = bb.getInt();
            byte[] tgtHash = new byte[32];
            bb.get(tgtHash);

            this.toBlockchainName = null;
            this.toBlockGlobalNumber = tgtNum;
            this.toBlockHash32 = tgtHash;

            this.message = readStrictUtf8Len16(bb, "EDIT_POST text");

            ensureNoTail(bb, "EDIT_POST");

        } else if (st == (MsgSubType.TEXT_REPLY & 0xFFFF)) {
            // REPLY: target(with bch) + text (без line)
            ensureMin(bb, 1 + 1 + 4 + 32 + 2, "REPLY too short");

            int nameLen = Byte.toUnsignedInt(bb.get());
            if (nameLen <= 0) throw new IllegalArgumentException("REPLY toBlockchainNameLen is 0");
            ensureMin(bb, nameLen + 4 + 32 + 2, "REPLY payload too short");

            byte[] nameBytes = new byte[nameLen];
            bb.get(nameBytes);
            this.toBlockchainName = new String(nameBytes, StandardCharsets.UTF_8);

            this.toBlockGlobalNumber = bb.getInt();

            this.toBlockHash32 = new byte[32];
            bb.get(this.toBlockHash32);

            this.message = readStrictUtf8Len16(bb, "REPLY text");

            // line fields отсутствуют в байтах
            this.lineCode = -1;
            this.prevLineNumber = -1;
            this.prevLineHash32 = null;
            this.thisLineNumber = -1;

            ensureNoTail(bb, "REPLY");

        } else if (st == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF)) {
            // EDIT_REPLY: target(no bch) + text (без line)
            ensureMin(bb, (4 + 32) + 2, "EDIT_REPLY too short");

            int tgtNum = bb.getInt();
            byte[] tgtHash = new byte[32];
            bb.get(tgtHash);

            this.toBlockchainName = null;
            this.toBlockGlobalNumber = tgtNum;
            this.toBlockHash32 = tgtHash;

            this.message = readStrictUtf8Len16(bb, "EDIT_REPLY text");

            // line fields отсутствуют в байтах
            this.lineCode = -1;
            this.prevLineNumber = -1;
            this.prevLineHash32 = null;
            this.thisLineNumber = -1;

            ensureNoTail(bb, "EDIT_REPLY");

        } else {
            throw new IllegalArgumentException("Unsupported Text subType: " + st);
        }
    }

    /* ===================================================================== */
    /* ====================== Фабрики (удобно) ============================= */
    /* ===================================================================== */

    public static TextBody newPost(int lineCode, int prevLineNumber, byte[] prevLineHash32, int thisLineNumber, String message) {
        return new TextBody(MsgSubType.TEXT_POST, lineCode, prevLineNumber, prevLineHash32, thisLineNumber,
                message, null, null, null);
    }

    public static TextBody newEditPost(int lineCode, int prevLineNumber, byte[] prevLineHash32, int thisLineNumber,
                                       int targetBlockNumber, byte[] targetHash32,
                                       String message) {
        return new TextBody(MsgSubType.TEXT_EDIT_POST, lineCode, prevLineNumber, prevLineHash32, thisLineNumber,
                message, null, targetBlockNumber, targetHash32);
    }

    public static TextBody newReply(String toBlockchainName, int targetBlockNumber, byte[] targetHash32, String message) {
        return new TextBody(MsgSubType.TEXT_REPLY, -1, -1, null, -1,
                message, toBlockchainName, targetBlockNumber, targetHash32);
    }

    public static TextBody newEditReply(int targetBlockNumber, byte[] targetHash32, String message) {
        return new TextBody(MsgSubType.TEXT_EDIT_REPLY, -1, -1, null, -1,
                message, null, targetBlockNumber, targetHash32);
    }

    /**
     * Универсальный конструктор “вручную”.
     * Для REPLY/EDIT_REPLY line поля игнорируются при сериализации (их в формате нет).
     */
    public TextBody(short subType,
                    int lineCode,
                    int prevLineNumber,
                    byte[] prevLineHash32,
                    int thisLineNumber,
                    String message,
                    String toBlockchainName,
                    Integer toBlockGlobalNumber,
                    byte[] toBlockHash32) {

        Objects.requireNonNull(message, "message == null");

        if (!isValidSubType(subType)) throw new IllegalArgumentException("Bad Text subType: " + (subType & 0xFFFF));
        if (message.isBlank()) throw new IllegalArgumentException("message is blank");

        this.subType = subType;
        this.version = VER;

        int st = subType & 0xFFFF;

        // line применима только к POST/EDIT_POST
        if (st == (MsgSubType.TEXT_POST & 0xFFFF) || st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            if (lineCode < 0) throw new IllegalArgumentException("lineCode < 0 for line message");
            this.lineCode = lineCode;
            this.prevLineNumber = prevLineNumber;
            this.prevLineHash32 = (prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
            this.thisLineNumber = thisLineNumber;
        } else {
            this.lineCode = -1;
            this.prevLineNumber = -1;
            this.prevLineHash32 = null;
            this.thisLineNumber = -1;
        }

        this.message = message;

        // target правила
        if (st == (MsgSubType.TEXT_POST & 0xFFFF)) {
            this.toBlockchainName = null;
            this.toBlockGlobalNumber = null;
            this.toBlockHash32 = null;

        } else if (st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            Objects.requireNonNull(toBlockGlobalNumber, "toBlockGlobalNumber == null");
            Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");
            if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
            if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

            this.toBlockchainName = null; // по ТЗ: не хранить
            this.toBlockGlobalNumber = toBlockGlobalNumber;
            this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);

        } else if (st == (MsgSubType.TEXT_REPLY & 0xFFFF)) {
            Objects.requireNonNull(toBlockchainName, "toBlockchainName == null");
            Objects.requireNonNull(toBlockGlobalNumber, "toBlockGlobalNumber == null");
            Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");
            if (toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
            if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
            if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

            this.toBlockchainName = toBlockchainName;
            this.toBlockGlobalNumber = toBlockGlobalNumber;
            this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);

        } else if (st == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF)) {
            Objects.requireNonNull(toBlockGlobalNumber, "toBlockGlobalNumber == null");
            Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");
            if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
            if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

            this.toBlockchainName = null; // по ТЗ: не хранить
            this.toBlockGlobalNumber = toBlockGlobalNumber;
            this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);

        } else {
            this.toBlockchainName = null;
            this.toBlockGlobalNumber = null;
            this.toBlockHash32 = null;
        }
    }

    private static boolean isValidSubType(short st) {
        int v = st & 0xFFFF;
        return v == (MsgSubType.TEXT_POST & 0xFFFF)
                || v == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)
                || v == (MsgSubType.TEXT_REPLY & 0xFFFF)
                || v == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF);
    }

    @Override
    public TextBody check() {
        if (!isValidSubType(subType))
            throw new IllegalArgumentException("Bad Text subType: " + (subType & 0xFFFF));

        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Text message is blank");

        int st = subType & 0xFFFF;

        // локальные проверки line (БД не трогаем)
        if (st == (MsgSubType.TEXT_POST & 0xFFFF) || st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            if (lineCode < 0) throw new IllegalArgumentException("lineCode < 0 for line message");
            if (prevLineHash32 == null || prevLineHash32.length != 32)
                throw new IllegalArgumentException("prevLineHash32 invalid");
        } else {
            // reply/edit_reply: line отсутствует
            if (prevLineHash32 != null)
                throw new IllegalArgumentException("REPLY/EDIT_REPLY must not contain line hash");
        }

        // target rules
        if (st == (MsgSubType.TEXT_POST & 0xFFFF)) {
            if (toBlockchainName != null || toBlockGlobalNumber != null || toBlockHash32 != null)
                throw new IllegalArgumentException("POST must not contain target fields");

        } else if (st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            if (toBlockchainName != null)
                throw new IllegalArgumentException("EDIT_POST must not contain toBlockchainName in target");
            if (toBlockGlobalNumber == null || toBlockGlobalNumber < 0)
                throw new IllegalArgumentException("EDIT_POST toBlockGlobalNumber invalid");
            if (toBlockHash32 == null || toBlockHash32.length != 32)
                throw new IllegalArgumentException("EDIT_POST toBlockHash32 invalid");

        } else if (st == (MsgSubType.TEXT_REPLY & 0xFFFF)) {
            if (toBlockchainName == null || toBlockchainName.isBlank())
                throw new IllegalArgumentException("REPLY toBlockchainName is blank");
            if (toBlockGlobalNumber == null || toBlockGlobalNumber < 0)
                throw new IllegalArgumentException("REPLY toBlockGlobalNumber invalid");
            if (toBlockHash32 == null || toBlockHash32.length != 32)
                throw new IllegalArgumentException("REPLY toBlockHash32 invalid");

        } else if (st == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF)) {
            if (toBlockchainName != null)
                throw new IllegalArgumentException("EDIT_REPLY must not contain toBlockchainName in target");
            if (toBlockGlobalNumber == null || toBlockGlobalNumber < 0)
                throw new IllegalArgumentException("EDIT_REPLY toBlockGlobalNumber invalid");
            if (toBlockHash32 == null || toBlockHash32.length != 32)
                throw new IllegalArgumentException("EDIT_REPLY toBlockHash32 invalid");
        }

        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] msgUtf8 = message.getBytes(StandardCharsets.UTF_8);
        if (msgUtf8.length == 0) throw new IllegalArgumentException("Text payload is empty");
        if (msgUtf8.length > 65535) throw new IllegalArgumentException("Text too long (>65535 bytes)");

        int st = subType & 0xFFFF;

        if (st == (MsgSubType.TEXT_POST & 0xFFFF)) {
            // hasLine(lineCode+line) + text
            int cap = (4 + 4 + 32 + 4) + 2 + msgUtf8.length;

            ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
            bb.putInt(lineCode);
            bb.putInt(prevLineNumber);
            bb.put(prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
            bb.putInt(thisLineNumber);
            bb.putShort((short) msgUtf8.length);
            bb.put(msgUtf8);
            return bb.array();

        } else if (st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
            // hasLine(lineCode+line) + target(no bch) + text
            if (toBlockGlobalNumber == null) throw new IllegalArgumentException("EDIT_POST missing toBlockGlobalNumber");
            if (toBlockHash32 == null || toBlockHash32.length != 32) throw new IllegalArgumentException("EDIT_POST toBlockHash32 != 32");

            int cap = (4 + 4 + 32 + 4) + (4 + 32) + 2 + msgUtf8.length;

            ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
            bb.putInt(lineCode);
            bb.putInt(prevLineNumber);
            bb.put(prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
            bb.putInt(thisLineNumber);

            bb.putInt(toBlockGlobalNumber);
            bb.put(toBlockHash32);

            bb.putShort((short) msgUtf8.length);
            bb.put(msgUtf8);
            return bb.array();

        } else if (st == (MsgSubType.TEXT_REPLY & 0xFFFF)) {
            // target(with bch) + text
            if (toBlockchainName == null) throw new IllegalArgumentException("REPLY missing toBlockchainName");
            if (toBlockGlobalNumber == null) throw new IllegalArgumentException("REPLY missing toBlockGlobalNumber");
            if (toBlockHash32 == null || toBlockHash32.length != 32) throw new IllegalArgumentException("REPLY toBlockHash32 != 32");

            byte[] nameUtf8 = toBlockchainName.getBytes(StandardCharsets.UTF_8);
            if (nameUtf8.length == 0 || nameUtf8.length > 255)
                throw new IllegalArgumentException("REPLY toBlockchainName utf8 len must be 1..255");

            int cap = 1 + nameUtf8.length + 4 + 32
                    + 2 + msgUtf8.length;

            ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
            bb.put((byte) nameUtf8.length);
            bb.put(nameUtf8);
            bb.putInt(toBlockGlobalNumber);
            bb.put(toBlockHash32);

            bb.putShort((short) msgUtf8.length);
            bb.put(msgUtf8);
            return bb.array();

        } else if (st == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF)) {
            // target(no bch) + text
            if (toBlockGlobalNumber == null) throw new IllegalArgumentException("EDIT_REPLY missing toBlockGlobalNumber");
            if (toBlockHash32 == null || toBlockHash32.length != 32) throw new IllegalArgumentException("EDIT_REPLY toBlockHash32 != 32");

            int cap = (4 + 32) + 2 + msgUtf8.length;

            ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
            bb.putInt(toBlockGlobalNumber);
            bb.put(toBlockHash32);

            bb.putShort((short) msgUtf8.length);
            bb.put(msgUtf8);
            return bb.array();

        } else {
            throw new IllegalStateException("Unsupported Text subType: " + st);
        }
    }

    /* ===================================================================== */
    /* ========================== Helpers ================================== */
    /* ===================================================================== */

    private static String readStrictUtf8Len16(ByteBuffer bb, String fieldName) {
        int len = Short.toUnsignedInt(bb.getShort());
        if (len <= 0) throw new IllegalArgumentException(fieldName + " is empty");
        if (bb.remaining() < len) throw new IllegalArgumentException(fieldName + " payload too short (len=" + len + ")");

        byte[] bytes = new byte[len];
        bb.get(bytes);

        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            String s = decoder.decode(ByteBuffer.wrap(bytes)).toString();
            if (s.isBlank()) throw new IllegalArgumentException(fieldName + " is blank");
            return s;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(fieldName + " is not valid UTF-8", e);
        }
    }

    private static void ensureMin(ByteBuffer bb, int need, String msg) {
        if (bb.remaining() < need) throw new IllegalArgumentException(msg + " (need=" + need + ", remaining=" + bb.remaining() + ")");
    }

    private static void ensureNoTail(ByteBuffer bb, String ctx) {
        if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes for " + ctx + ", remaining=" + bb.remaining());
    }

    /* ====================== BodyHasLine ====================== */
    @Override public int lineCode() { return lineCode; }
    @Override public int prevLineBlockGlobalNumber() { return prevLineNumber; }
    @Override public byte[] prevLineBlockHash32() {
        if (prevLineHash32 == null) return null;
        return Arrays.copyOf(prevLineHash32, 32);
    }
    @Override public int lineSeq() { return thisLineNumber; }

    /* ====================== BodyHasTarget ===================== */
    @Override public String toBchName() { return toBlockchainName; }
    @Override public Integer toBlockGlobalNumber() { return toBlockGlobalNumber; }
    @Override public byte[] toBlockHashBytes() { return toBlockHash32; }

    /* ===================================================================== */
    /* ===================== Удобные хелперы (для ChainState) =============== */
    /* ===================================================================== */

    /** true только для POST / EDIT_POST (т.е. это сообщение в линии канала). */
    public boolean isLineMessage() {
        int st = subType & 0xFFFF;
        return st == (MsgSubType.TEXT_POST & 0xFFFF)
                || st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF);
    }

    /** true только для EDIT_POST / EDIT_REPLY. */
    public boolean isEditMessage() {
        int st = subType & 0xFFFF;
        return st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)
                || st == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF);
    }

    /** true только для REPLY / EDIT_REPLY (т.е. “не в линии”). */
    public boolean isReplyFamily() {
        int st = subType & 0xFFFF;
        return st == (MsgSubType.TEXT_REPLY & 0xFFFF)
                || st == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF);
    }
}