package test.it;

public final class TestLog {
    private TestLog(){}

    // включается так: ./gradlew test -Dit.verbose=true
    public static final boolean VERBOSE = true; //Boolean.parseBoolean(System.getProperty("it.verbose", "false"));

    public static void info(String s) {
        if (VERBOSE) System.out.println(s);
    }

    public static void section(String title) {
        if (!VERBOSE) return;
        System.out.println("\n\n==================================================");
        System.out.println(title);
        System.out.println("==================================================\n");
    }

    public static void req(String title, String json) {
        if (!VERBOSE) return;
        System.out.println("\n📤 " + title);
        System.out.println(json);
    }

    public static void resp(String title, String json) {
        if (!VERBOSE) return;
        System.out.println("\n📥 " + title);
        System.out.println(json);
        System.out.println("-----------------------------------------------------");
    }
}