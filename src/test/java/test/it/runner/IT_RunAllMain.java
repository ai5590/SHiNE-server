package test.it.runner;

import test.it.cases.IT_01_AddUser;
import test.it.cases.IT_02_Sessions;
import test.it.cases.IT_03_AddBlock_NoAuth;
import test.it.cases.IT_04_UserParams_NoAuth;
import test.it.utils.log.TestLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Ручной запуск всех IT тестов БЕЗ JUnit.
 * Печатает итоги по каждому тесту отдельной строкой.
 */
public class IT_RunAllMain {

    public static void main(String[] args) {
        int failed = runAll();
    }

    public static int runAll() {

        List<String> summaries = new ArrayList<>();
        int failed = 0;

        TestLog.title("IT RUN: запуск всех тестов подряд");

        String s1 = IT_01_AddUser.run(); summaries.add(s1); if (s1.contains("FAIL:")) failed++;
        String s2 = IT_02_Sessions.run(); summaries.add(s2); if (s2.contains("FAIL:")) failed++;
        String s3 = IT_03_AddBlock_NoAuth.run(); summaries.add(s3); if (s3.contains("FAIL:")) failed++;
        String s4 = IT_04_UserParams_NoAuth.run(); summaries.add(s4); if (s4.contains("FAIL:")) failed++;

        TestLog.title("IT RUN RESULT (per test)");
        for (String s : summaries) System.out.println(s);

        if (failed == 0) TestLog.ok("\n  ВСЕ IT ТЕСТЫ УСПЕШНО ЗАВЕРШЕНЫ");
        else TestLog.boom("❌ IT ПРОГОН УПАЛ: failed=" + failed + " из " + summaries.size());

        return failed;
    }
}