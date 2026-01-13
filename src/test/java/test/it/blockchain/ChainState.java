package test.it.blockchain;

import blockchain.LineIndex;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * ChainState — состояние цепочки + состояние линий (только тех, где они нужны):
 *
 * Глобальная цепочка:
 *  - lastBlockNumber / lastBlockHashHex
 *  - map blockNumber -> hash32 (для ссылок reply/edit/reaction)
 *
 * Линии (по ТЗ нужны):
 *  - TEXT (1)
 *  - CONNECTION (3)
 *  - USER_PARAM (4)
 *
 * prevLineNumber по ТЗ — это GLOBAL blockNumber предыдущего блока линии.
 * thisLineNumber — внутренний номер линии (мы ведём локально: 1,2,3...)
 */
public final class ChainState {

    public static final int LINES_MAX = 8;

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    // global chain
    private int lastBlockNumber = -1;
    private String lastBlockHashHex = ZERO64;

    // header (block#0)
    private byte[] headerHash32 = null;

    // per-line state (только для LineIndex.TEXT/CONNECTION/USER_PARAM)
    private final int[] lineLastGlobalNumber = new int[LINES_MAX];     // последний GLOBAL номер блока в линии
    private final String[] lineLastHashHex = new String[LINES_MAX];    // hash последнего блока линии
    private final int[] lineLastThisLineNumber = new int[LINES_MAX];   // последний thisLineNumber (внутренний)

    private final Map<Integer, byte[]> hash32ByNumber = new HashMap<>();

    public ChainState() {
        Arrays.fill(lineLastGlobalNumber, -1);
        Arrays.fill(lineLastHashHex, "");
        Arrays.fill(lineLastThisLineNumber, 0);
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

    /** Следующие line-поля для указанной линии (только TEXT/CONNECTION/USER_PARAM). */
    public NextLine nextLine(short lineIndex) {
        checkLine(lineIndex);
        if (!isLineUsed(lineIndex)) {
            throw new IllegalArgumentException("Line " + lineIndex + " не используется для BodyHasLine по ТЗ");
        }
        if (!hasHeader()) {
            throw new IllegalStateException("Нельзя формировать line-поля до HEADER (нет headerHash32)");
        }

        int lastGlobal = lineLastGlobalNumber[lineIndex];
        int lastThis = lineLastThisLineNumber[lineIndex];

        if (lastGlobal == -1) {
            // первый блок линии ссылается на HEADER (block#0)
            return new NextLine(0, headerHash32.clone(), 1);
        }

        String lastHex = lineLastHashHex[lineIndex];
        if (lastHex == null || lastHex.isBlank()) {
            throw new IllegalStateException("lineLastHashHex[" + lineIndex + "] пуст, но lastGlobal!=-1");
        }

        return new NextLine(lastGlobal, hexToBytes32(lastHex), lastThis + 1);
    }

    // -------------------- apply --------------------

    public void applyAppendedBlock(int blockNumber, byte[] hash32, boolean isHeader, short type) {
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

        // обновляем line-state только для линий, которые "надо" по ТЗ
        short lineIndex = lineIndexByType(type);
        if (lineIndex != -1 && isLineUsed(lineIndex)) {
            lineLastGlobalNumber[lineIndex] = blockNumber;
            lineLastHashHex[lineIndex] = hex64;

            // thisLineNumber мы берём из тела, но здесь его нет.
            // Поэтому thisLineNumber должен обновляться там, где формируются тела (в тестах),
            // либо AddBlockSender может прокинуть его отдельно.
            // Чтобы не дублировать контракт — здесь оставляем как есть.
        }
    }

    /** В тестах удобно явно обновлять thisLineNumber после успешной отправки line-body. */
    public void applyThisLineNumber(short lineIndex, int thisLineNumber) {
        checkLine(lineIndex);
        if (!isLineUsed(lineIndex)) return;
        lineLastThisLineNumber[lineIndex] = thisLineNumber;
    }

    // -------------------- mapping --------------------

    /** По type блока определяем lineIndex. Reaction line по твоему ТЗ "не надо". */
    private static short lineIndexByType(short type) {
        int t = type & 0xFFFF;
        return switch (t) {
            case 0 -> LineIndex.HEADER;
            case 1 -> LineIndex.TEXT;
            case 3 -> LineIndex.CONNECTION;
            case 4 -> LineIndex.USER_PARAM;
            default -> (short) -1; // reaction/unknown => line state not used
        };
    }

    private static boolean isLineUsed(short lineIndex) {
        return lineIndex == LineIndex.TEXT
                || lineIndex == LineIndex.CONNECTION
                || lineIndex == LineIndex.USER_PARAM;
    }

    private static void checkLine(short lineIndex) {
        if (lineIndex < 0 || lineIndex >= LINES_MAX) {
            throw new IllegalArgumentException("lineIndex must be 0.." + (LINES_MAX - 1));
        }
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