package test.it;

import test.it.utils.ItRunContext;
import test.it.utils.TestLog;

/**
 * Ручной запуск всех IT тестов БЕЗ JUnit / Suite.
 */
public class IT_RunAllMain {

    public static void main(String[] args) {
        ItRunContext.initIfNeeded();

        int failed = runAll();
        System.exit(failed);
    }

    public static int runAll() {

        final int total = 4; // было 3
        int failed = 0;
        int passed = 0;

        TestLog.title("IT RUN: запуск всех тестов подряд (без очистки data/)");

        TestLog.stepTitle("RUN: IT_01_AddUser");
        int f1 = IT_01_AddUser.run();
        failed += f1; passed += (f1 == 0 ? 1 : 0);

        TestLog.stepTitle("RUN: IT_02_Sessions");
        int f2 = IT_02_Sessions.run();
        failed += f2; passed += (f2 == 0 ? 1 : 0);

        TestLog.stepTitle("RUN: IT_03_AddBlock_NoAuth (combined 3+4)");
        int f3 = IT_03_AddBlock_NoAuth.run();
        failed += f3; passed += (f3 == 0 ? 1 : 0);

        TestLog.stepTitle("RUN: IT_04_UserParams_NoAuth");
        int f4 = IT_04_UserParams_NoAuth.run();
        failed += f4; passed += (f4 == 0 ? 1 : 0);

        TestLog.titleBlock("""
                IT RUN RESULT
                ----------------------------
                total  = %d
                passed = %d
                failed = %d
                """.formatted(total, passed, failed));

        if (failed == 0) {
            TestLog.ok("✅ ВСЕ IT ТЕСТЫ УСПЕШНО ЗАВЕРШЕНЫ");
        } else {
            TestLog.boom("❌ IT ПРОГОН УПАЛ: failed=" + failed + " из " + total);
        }

        return failed;
    }
}