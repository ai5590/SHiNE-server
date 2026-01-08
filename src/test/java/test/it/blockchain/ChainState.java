package test.it.blockchain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * ChainState — только состояние цепочки (номера/хэши).
 *
 * Хранит:
 *  - last globalNumber / last globalHash
 *  - last lineNum / last lineHash по каждой линии
 *  - hash32 нулевого блока (headerHash32) — нужен как prevLineHash для первого блока каждой линии
 *  - map globalNumber -> hash32 (для ссылок reply/reaction на старые блоки)
 */
public final class ChainState {

    public static final int LINES_MAX = 8;

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    private final int[] lineLastNumber = new int[LINES_MAX];
    private final String[] lineLastHashHex = new String[LINES_MAX];

    private int globalLastNumber = -1;
    private String globalLastHashHex = ZERO64;

    private byte[] headerHash32 = null;

    // Для удобства тестов: чтобы можно было делать reply/like на любой уже отправленный globalNumber
    private final Map<Integer, byte[]> globalHash32ByNumber = new HashMap<>();

    public ChainState() {
        Arrays.fill(lineLastHashHex, "");
        // lineLastNumber по умолчанию = 0
    }

    // -------------------- getters --------------------

    public int globalLastNumber() { return globalLastNumber; }
    public String globalLastHashHex() { return globalLastHashHex; }

    public int lineLastNumber(short line) { return lineLastNumber[line]; }
    public String lineLastHashHex(short line) { return lineLastHashHex[line]; }

    public byte[] headerHash32() { return headerHash32 == null ? null : headerHash32.clone(); }

    public byte[] getGlobalHash32(int globalNumber) {
        byte[] h = globalHash32ByNumber.get(globalNumber);
        return h == null ? null : h.clone();
    }

    // -------------------- state helpers --------------------

    public boolean hasHeader() {
        return headerHash32 != null && headerHash32.length == 32 && globalLastNumber >= 0;
    }

    /** Следующий globalNumber. */
    public int nextGlobalNumber() {
        return globalLastNumber + 1;
    }

    /** Следующий lineNumber: для line>0 — last+1. Для line0 — всегда 0 (header). */
    public int nextLineNumber(short lineIndex) {
        checkLine(lineIndex);
        if (lineIndex == 0) return 0;
        return lineLastNumber[lineIndex] + 1;
    }

    /** prevGlobalHash32: для header это ZERO32, иначе hash последнего глобального блока. */
    public byte[] prevGlobalHash32ForNext(short nextLineIndex) {
        // Для genesis/header prevGlobalHash = ZERO32
        if (globalLastNumber < 0) return ZERO32;
        return hexToBytes32(globalLastHashHex);
    }

    /**
     * prevLineHash32 по твоему правилу:
     *  - для line0 (header) — ZERO32
     *  - для первого блока линии (lineLastNumber[line]==0) — hash нулевого блока (headerHash32)
     *  - иначе — hash последнего блока этой линии
     */
    public byte[] prevLineHash32ForNext(short lineIndex) {
        checkLine(lineIndex);
        if (lineIndex == 0) return ZERO32;

        if (lineLastNumber[lineIndex] == 0) {
            if (headerHash32 == null) {
                throw new IllegalStateException("headerHash32 is not set but required for first block of line " + lineIndex);
            }
            return headerHash32.clone();
        }

        String lastHex = lineLastHashHex[lineIndex];
        if (lastHex == null || lastHex.isBlank()) {
            throw new IllegalStateException("lineLastHashHex[" + lineIndex + "] is blank but lineLastNumber>0");
        }
        return hexToBytes32(lastHex);
    }

    /**
     * Применить факт успешного добавления блока:
     *  - обновить global last
     *  - обновить line last
     *  - сохранить globalNumber->hash32
     *  - если это header: сохранить headerHash32
     */
    public void applyAppendedBlock(int globalNumber,
                                   short lineIndex,
                                   int lineNumber,
                                   byte[] hash32) {

        if (hash32 == null || hash32.length != 32) {
            throw new IllegalArgumentException("hash32 must be 32 bytes");
        }

        // базовые ожидания по номерам (для тестов строго)
        if (globalNumber != globalLastNumber + 1) {
            throw new IllegalStateException("globalNumber sequence broken: expected=" + (globalLastNumber + 1) + " got=" + globalNumber);
        }

        checkLine(lineIndex);

        if (lineIndex == 0) {
            if (globalNumber != 0 || lineNumber != 0) {
                throw new IllegalStateException("Header must be global=0 line=0 lineNum=0");
            }
            headerHash32 = hash32.clone();
        } else {
            int expectedLineNum = lineLastNumber[lineIndex] + 1;
            if (lineNumber != expectedLineNum) {
                throw new IllegalStateException("lineNumber sequence broken for line=" + lineIndex +
                        ": expected=" + expectedLineNum + " got=" + lineNumber);
            }
        }

        String hex64 = bytesToHex64(hash32);

        globalLastNumber = globalNumber;
        globalLastHashHex = hex64;

        lineLastNumber[lineIndex] = lineNumber;
        lineLastHashHex[lineIndex] = hex64;

        globalHash32ByNumber.put(globalNumber, hash32.clone());
    }

    // -------------------- utils --------------------

    private static void checkLine(short lineIndex) {
        if (lineIndex < 0 || lineIndex >= LINES_MAX) {
            throw new IllegalArgumentException("lineIndex must be 0.." + (LINES_MAX - 1));
        }
    }

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