package test.it.blockchain;

import blockchain.MsgSubType;
import blockchain.body.BodyRecord;
import blockchain.body.BodyHasLine;
import blockchain.body.CreateChannelBody;
import blockchain.body.TextBody;

import java.util.HashMap;
import java.util.Map;

/**
 * ChainState — состояние глобальной цепочки + состояние линий.
 *
 * Глобальная цепочка:
 *  - lastBlockNumber / lastBlockHashHex
 *  - map blockNumber -> hash32
 *
 * Линии:
 *  - TECH (type=0): только CREATE_CHANNEL (hasLine), root = HEADER
 *  - TEXT (type=1): линии каналов, root = HEADER (канал "0") или CREATE_CHANNEL (канал "X")
 *  - CONNECTION (type=3): одна линия
 *  - USER_PARAM (type=4): одна линия
 *
 * Важно:
 *  - prevLineNumber — это GLOBAL blockNumber предыдущего блока линии.
 *  - thisLineNumber — внутренний номер линии (для постов: 0,1,2...; для тех-линии: 1,2,3...)
 */
public final class ChainState {

    public static final short TYPE_TECH       = 0; // header/create_channel
    public static final short TYPE_TEXT       = 1;
    public static final short TYPE_REACTION   = 2;
    public static final short TYPE_CONNECTION = 3;
    public static final short TYPE_USER_PARAM = 4;

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    // global chain
    private int lastBlockNumber = -1;
    private String lastBlockHashHex = ZERO64;

    // header (block#0)
    private byte[] headerHash32 = null;

    private final Map<Integer, byte[]> hash32ByNumber = new HashMap<>();

    // ---------- TECH line state ----------
    private static final class TechLineState {
        int lastGlobalNumber = -1;   // последний TECH-блок (HEADER или CREATE_CHANNEL)
        String lastHashHex = "";
        int lastThisLineNumber = 0;  // 0 у HEADER (логически), дальше 1,2,3...

        void reset() {
            lastGlobalNumber = -1;
            lastHashHex = "";
            lastThisLineNumber = 0;
        }
    }

    private final TechLineState techLine = new TechLineState();

    // ---------- CONNECTION/USER_PARAM line state ----------
    private static final class SimpleLineState {
        int lastGlobalNumber = -1;
        String lastHashHex = "";
        int lastThisLineNumber = 0;

        void reset() {
            lastGlobalNumber = -1;
            lastHashHex = "";
            lastThisLineNumber = 0;
        }
    }

    private final SimpleLineState connectionLine = new SimpleLineState();
    private final SimpleLineState userParamLine = new SimpleLineState();

    // ---------- TEXT channels ----------
    public static final class ChannelLineState {
        final int rootBlockNumber;
        final String rootHashHex;

        int lastGlobalNumber;
        String lastHashHex;
        int lastThisLineNumber; // перед первым постом = -1, чтобы первый был 0

        ChannelLineState(int rootBlockNumber, String rootHashHex) {
            this.rootBlockNumber = rootBlockNumber;
            this.rootHashHex = rootHashHex;
            this.lastGlobalNumber = rootBlockNumber;
            this.lastHashHex = rootHashHex;
            this.lastThisLineNumber = -1;
        }
    }

    // rootBlockNumber -> state
    private final Map<Integer, ChannelLineState> textChannels = new HashMap<>();

    public ChainState() {
        techLine.reset();
        connectionLine.reset();
        userParamLine.reset();
    }

    // -------------------- global getters --------------------

    public int lastBlockNumber() { return lastBlockNumber; }
    public String lastBlockHashHex() { return lastBlockHashHex; }

    public boolean hasHeader() {
        return headerHash32 != null && headerHash32.length == 32 && lastBlockNumber >= 0;
    }

    public int nextBlockNumber() {
        return lastBlockNumber + 1;
    }

    public byte[] prevHash32ForNext() {
        if (lastBlockNumber < 0) return ZERO32;
        return hexToBytes32(lastBlockHashHex);
    }

    public byte[] headerHash32() {
        return headerHash32 == null ? null : headerHash32.clone();
    }

    public byte[] getHash32(int blockNumber) {
        byte[] h = hash32ByNumber.get(blockNumber);
        return h == null ? null : h.clone();
    }

    // -------------------- line helpers --------------------

    public static final class NextLine {
        public final int prevLineNumber;     // GLOBAL blockNumber
        public final byte[] prevLineHash32;  // 32 bytes
        public final int thisLineNumber;     // внутр. номер линии

        public NextLine(int prevLineNumber, byte[] prevLineHash32, int thisLineNumber) {
            this.prevLineNumber = prevLineNumber;
            this.prevLineHash32 = (prevLineHash32 == null ? null : prevLineHash32.clone());
            this.thisLineNumber = thisLineNumber;
        }
    }

    /** Следующие line-поля для TECH/CONNECTION/USER_PARAM. */
    public NextLine nextLineByType(short type) {
        if (!hasHeader()) {
            throw new IllegalStateException("Нельзя формировать line-поля до HEADER (нет headerHash32)");
        }

        int t = type & 0xFFFF;

        if (t == TYPE_TECH) {
            // tech-line: prev = последний TECH; первый CREATE_CHANNEL -> prev = HEADER
            if (techLine.lastGlobalNumber == -1) {
                // после HEADER мы должны инициализировать techLine (делаем в applyHeader)
                throw new IllegalStateException("TECH line is not initialized yet");
            }
            return new NextLine(techLine.lastGlobalNumber, hexToBytes32(techLine.lastHashHex), techLine.lastThisLineNumber + 1);
        }

        if (t == TYPE_CONNECTION) {
            return nextSimpleLine(connectionLine);
        }
        if (t == TYPE_USER_PARAM) {
            return nextSimpleLine(userParamLine);
        }

        throw new IllegalArgumentException("Type " + t + " не поддерживает nextLineByType()");
    }

    private NextLine nextSimpleLine(SimpleLineState ls) {
        if (ls.lastGlobalNumber == -1) {
            // первый блок линии ссылается на HEADER (block#0)
            return new NextLine(0, headerHash32.clone(), 1);
        }
        if (ls.lastHashHex == null || ls.lastHashHex.isBlank()) {
            throw new IllegalStateException("LineState.lastHashHex пуст, но lastGlobalNumber!=-1");
        }
        return new NextLine(ls.lastGlobalNumber, hexToBytes32(ls.lastHashHex), ls.lastThisLineNumber + 1);
    }

    /** Следующие line-поля для TEXT-канала по rootBlockNumber. */
    public NextLine nextTextLineByRoot(int rootBlockNumber) {
        if (!hasHeader()) throw new IllegalStateException("No HEADER");
        ChannelLineState cs = textChannels.get(rootBlockNumber);
        if (cs == null) throw new IllegalStateException("Unknown TEXT channel rootBlockNumber=" + rootBlockNumber);

        return new NextLine(
                cs.lastGlobalNumber,
                hexToBytes32(cs.lastHashHex),
                cs.lastThisLineNumber + 1
        );
    }

    /** Зарегистрировать новый канал TEXT по root = CREATE_CHANNEL block. */
    public void registerTextChannelRoot(int rootBlockNumber, byte[] rootHash32) {
        if (rootBlockNumber <= 0) throw new IllegalArgumentException("rootBlockNumber must be > 0 for custom channel");
        if (rootHash32 == null || rootHash32.length != 32) throw new IllegalArgumentException("rootHash32 invalid");
        textChannels.put(rootBlockNumber, new ChannelLineState(rootBlockNumber, bytesToHex64(rootHash32)));
    }

    /** root канала "0" (по умолчанию) — это HEADER block#0. */
    public int rootChannel0() {
        return 0;
    }

    // -------------------- apply --------------------

    public void applyAppendedBlock(int blockNumber, byte[] hash32, boolean isHeader, short type, BodyRecord body) {
        if (hash32 == null || hash32.length != 32) {
            throw new IllegalArgumentException("hash32 must be 32 bytes");
        }
        if (blockNumber != lastBlockNumber + 1) {
            throw new IllegalStateException("blockNumber sequence broken: expected=" + (lastBlockNumber + 1) + " got=" + blockNumber);
        }

        if (isHeader) {
            if (blockNumber != 0) throw new IllegalStateException("HEADER must be blockNumber=0");
            headerHash32 = hash32.clone();
        } else {
            if (blockNumber == 0) throw new IllegalStateException("Non-header block can't be blockNumber=0");
            if (headerHash32 == null) throw new IllegalStateException("Header must be sent before non-header blocks");
        }

        String hex64 = bytesToHex64(hash32);

        lastBlockNumber = blockNumber;
        lastBlockHashHex = hex64;

        hash32ByNumber.put(blockNumber, hash32.clone());

        // ---- init after HEADER ----
        if (isHeader) {
            // TECH line root = HEADER
            techLine.lastGlobalNumber = 0;
            techLine.lastHashHex = hex64;
            techLine.lastThisLineNumber = 0;

            // TEXT channel "0" root = HEADER, первый пост будет thisLineNumber=0
            textChannels.put(0, new ChannelLineState(0, hex64));

            return;
        }

        int t = type & 0xFFFF;

        // ---- TECH (CREATE_CHANNEL) ----
        if (t == TYPE_TECH && body instanceof CreateChannelBody ccb) {
            techLine.lastGlobalNumber = blockNumber;
            techLine.lastHashHex = hex64;
            techLine.lastThisLineNumber = ccb.thisLineNumber;
            return;
        }

        // ---- CONNECTION / USER_PARAM ----
        if (t == TYPE_CONNECTION && body instanceof BodyHasLine hlc) {
            connectionLine.lastGlobalNumber = blockNumber;
            connectionLine.lastHashHex = hex64;
            connectionLine.lastThisLineNumber = hlc.thisLineNumber();
            return;
        }
        if (t == TYPE_USER_PARAM && body instanceof BodyHasLine hlu) {
            userParamLine.lastGlobalNumber = blockNumber;
            userParamLine.lastHashHex = hex64;
            userParamLine.lastThisLineNumber = hlu.thisLineNumber();
            return;
        }

        // ---- TEXT channels (POST/EDIT_POST) ----
        if (t == TYPE_TEXT && body instanceof TextBody tb) {
            if (tb.isLineMessage()) {
                // ищем канал по совпадению prevLineNumber с lastGlobalNumber канала
                ChannelLineState channel = findTextChannelByLastGlobal(tb.prevLineNumber);
                if (channel == null) {
                    throw new IllegalStateException("TEXT line message prevLineNumber=" + tb.prevLineNumber + " не привязан ни к одному каналу (канал root не зарегистрирован?)");
                }

                channel.lastGlobalNumber = blockNumber;
                channel.lastHashHex = hex64;
                channel.lastThisLineNumber = tb.thisLineNumber;
            }
        }
    }

    private ChannelLineState findTextChannelByLastGlobal(int prevLineNumber) {
        for (ChannelLineState cs : textChannels.values()) {
            if (cs.lastGlobalNumber == prevLineNumber) return cs;
        }
        return null;
    }

    // -------------------- utils --------------------

    private static byte[] hexToBytes32(String hex) {
        if (hex == null) throw new IllegalArgumentException("hex is null");
        String s = hex.trim();
        if (s.length() != 64) throw new IllegalArgumentException("hex must be 64 chars, got " + s.length());
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad hex at pos " + (i * 2));
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String bytesToHex64(byte[] b32) {
        char[] out = new char[64];
        final char[] HEX = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 32; i++) {
            int v = b32[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}