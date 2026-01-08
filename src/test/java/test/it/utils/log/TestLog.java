package test.it.utils.log;

import test.it.utils.TestConfig;

/**
 * TestLog — единое место для:
 *  - ANSI цветов
 *  - стандартных сообщений (title/step/send/recv)
 *  - PASS/FAIL строк и окраски
 *
 * Режим:
 *  - it.debug=false: печатаем минимум (без JSON)
 *  - it.debug=true: печатаем JSON отправка/ответ + заголовки шагов
 */
public final class TestLog {
    private TestLog() {}

    public static final boolean DEBUG = TestConfig.DEBUG();

    // ANSI COLORS (ТОЛЬКО ТУТ)
    public static final String R   = "\u001B[0m";
    public static final String G   = "\u001B[32m";
    public static final String Y   = "\u001B[33m";
    public static final String RED = "\u001B[31m";
    public static final String C   = "\u001B[36m";

    public static String green(String s) { return G + s + R; }
    public static String red(String s)   { return RED + s + R; }
    public static String cyan(String s)  { return C + s + R; }

    /** Инфо (печатается только при DEBUG=true). */
    public static void info(String s) {
        if (DEBUG) System.out.println(s);
    }

    public static void line() {
        if (!DEBUG) return;
        System.out.println(C + "------------------------------------------------------------" + R);
    }

    public static void title(String s) {
        if (!DEBUG) return;
        System.out.println(C + "\n============================================================" + R);
        System.out.println(C + s + R);
        System.out.println(C + "============================================================\n" + R);
    }

    public static void titleBlock(String multiLineText) {
        if (!DEBUG) return;
        System.out.println(C + "\n============================================================" + R);
        System.out.println(C + multiLineText + R);
        System.out.println(C + "============================================================\n" + R);
    }

    public static void stepTitle(String s) {
        if (!DEBUG) return;
        System.out.println(C + "\n-------------------- " + s + " --------------------" + R);
    }

    /** OK (печатаем ВСЕГДА, чтобы было видно зелёное прохождение шагов). */
    public static void ok(String s) {
        System.out.println(G + "✅ " + s + R);
    }

    /** WARN (только DEBUG). */
    public static void warn(String s) {
        if (!DEBUG) return;
        System.out.println(Y + "⚠️ " + s + R);
    }

    /** FAIL (печатаем ВСЕГДА). */
    public static void boom(String s) {
        System.out.println(RED + "****************************************************************" + R);
        System.out.println(RED + "❌ " + s + R);
        System.out.println(RED + "****************************************************************" + R);
    }

    public static void send(String op, String json) {
        if (!DEBUG) return;
        System.out.println("📤 [" + op + "] Request JSON:");
        System.out.println(json);
        line();
    }

    public static void recv(String op, String json) {
        if (!DEBUG) return;
        System.out.println("📥 [" + op + "] Response JSON:");
        System.out.println(json);
        line();
    }
}