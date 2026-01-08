package test.it.suite;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import test.it.cases.IT_01_AddUser;
import test.it.cases.IT_02_Sessions;
import test.it.cases.IT_03_AddBlock_NoAuth;

/**
 * Сьют, который запускает IT тесты строго в заданном порядке.
 *
 * Запуск:
 *   ./gradlew test --tests test.it.suite.IT_00_Suite
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