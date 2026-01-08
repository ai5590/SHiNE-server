package test.it.utils.log;

import java.util.ArrayList;
import java.util.List;

/**
 * TestResult — накопитель результатов внутри одного теста:
 *  - ok(...) печатает зелёным
 *  - fail(...) печатает красным и добавляет в итоговую строку
 *  - summaryLine() возвращает одну строку: PASS/FAIL + детали
 */
public final class TestResult {

    private final String testName;
    private final List<String> errors = new ArrayList<>();

    public TestResult(String testName) {
        this.testName = testName;
    }

    public void ok(String msg) {
        TestLog.ok(msg);
    }

    public void fail(String msg) {
        errors.add(msg);
        TestLog.boom(msg);
    }

    public boolean isOk() {
        return errors.isEmpty();
    }

    public String summaryLine() {
        if (errors.isEmpty()) {
            return TestLog.green("PASS: " + testName + " — OK");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(TestLog.red("FAIL: ")).append(testName).append(" — ").append(errors.size()).append(" ошибок: ");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(errors.get(i));
        }
        return sb.toString();
    }
}