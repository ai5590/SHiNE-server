package test.it.utils;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.engine.TestExecutionResult;


import java.util.ArrayList;
import java.util.List;

/**
 * Слушатель JUnit, который в конце прогона печатает "главный отчёт" по-русски.
 *
 * Подключается через src/test/resources/junit-platform.properties
 */
public class RussianSummaryListener implements TestExecutionListener {

    private final List<String> failed = new ArrayList<>();
    private int total = 0;
    private int passed = 0;
    private int skipped = 0;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        // Инициализируем данные прогона прямо тут, чтобы гарантированно до тестов
        ItRunContext.initOnce();
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!testIdentifier.isTest()) return;

        total++;

        switch (testExecutionResult.getStatus()) {
            case SUCCESSFUL -> passed++;
            case ABORTED -> skipped++;
            case FAILED -> {
                String name = prettyName(testIdentifier);
                String msg = testExecutionResult.getThrowable()
                        .map(t -> t.getClass().getSimpleName() + ": " + safeMsg(t.getMessage()))
                        .orElse("Причина неизвестна");
                failed.add(name + " — " + msg);
            }
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.out.println(TestColors.C + "\n============================================================" + TestColors.R);
        System.out.println(TestColors.C + "ГЛАВНЫЙ ОТЧЁТ IT ПРОГОНА" + TestColors.R);
        System.out.println(TestColors.C + "============================================================" + TestColors.R);

        System.out.println("Всего тестов: " + total);
        System.out.println(TestColors.G + "Прошло:      " + passed + TestColors.R);
        System.out.println(TestColors.Y + "Пропущено:   " + skipped + TestColors.R);
        System.out.println(TestColors.RED + "Упало:       " + failed.size() + TestColors.R);

        if (failed.isEmpty()) {
            System.out.println(TestColors.G + "\n✅ ВСЕ ТЕСТЫ ПРОШЛИ УСПЕШНО" + TestColors.R);
        } else {
            System.out.println(TestColors.RED + "\n❌ СПИСОК УПАВШИХ ТЕСТОВ:" + TestColors.R);
            for (int i = 0; i < failed.size(); i++) {
                System.out.println(TestColors.RED + (i + 1) + ") " + failed.get(i) + TestColors.R);
            }
        }

        System.out.println(TestColors.C + "------------------------------------------------------------" + TestColors.R);
        System.out.println("login          = " + ItRunContext.login());
        System.out.println("blockchainName = " + ItRunContext.blockchainName());
        System.out.println(TestColors.C + "============================================================\n" + TestColors.R);
    }

    private static String prettyName(TestIdentifier id) {
        // displayName обычно типа: "addUser_shouldReturn200_orAlreadyExists()"
        // Добавим класс, если удастся вытащить из legacyId (обычно там есть полное имя)
        String dn = id.getDisplayName();
        String legacy = id.getLegacyReportingName(); // часто содержит "test.it.IT_01_AddUser"
        if (legacy != null && !legacy.isBlank()) {
            return legacy + " :: " + (dn == null ? "test" : dn);
        }
        return dn == null ? "test" : dn;
    }

    private static String safeMsg(String s) {
        if (s == null) return "";
        return s.replace("\n", " ").replace("\r", " ");
    }
}