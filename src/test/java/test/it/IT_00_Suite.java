package test.it;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import test.it.ws.*;

/**
 * Сьют, который запускает IT тесты строго в заданном порядке.
 *
 * Запуск:
 *   ./gradlew test --tests test.it.IT_00_Suite
 *
 * Важно:
 *  - ItRunContext.initOnce() будет вызван слушателем RussianSummaryListener ДО тестов.
 */
@Suite
@SelectClasses({
        IT_01_AddUser.class,
        IT_02_Sessions.class,
        IT_03_AddBlock_NoAuth.class
})
public class IT_00_Suite {
    // пусто
}