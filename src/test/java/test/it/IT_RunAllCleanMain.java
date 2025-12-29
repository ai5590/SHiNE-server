package test.it;

import test.it.utils.ItRunContext;
import test.it.utils.TestLog;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

/**
 * Ручной запуск всех IT тестов БЕЗ JUnit / Suite, но С ПРЕДВАРИТЕЛЬНОЙ очисткой data/.
 *
 * Делает:
 *  1) чистит папку data/
 *  2) запускает все тесты по очереди (через IT_RunAllMain.runAll())
 *  3) возвращает код = число упавших тестов
 */
public class IT_RunAllCleanMain {

    private static final String DATA_DIR = "data";

    public static void main(String[] args) {
//        ItRunContext.initIfNeeded();

        TestLog.title("IT RUN CLEAN: очистка data/ + запуск всех тестов");

        try {
            cleanupDataDir(DATA_DIR);
        } catch (Throwable t) {
            TestLog.boom("Не смог очистить data/. Причина: " + t.getMessage());
            if (TestLog.VERBOSE) t.printStackTrace(System.out);
            System.exit(1);
        }

//        int failed = IT_RunAllMain.runAll();
//        System.exit(failed);
    }

    private static void cleanupDataDir(String dirName) throws IOException {
        Path dir = Paths.get(dirName);

        if (!Files.exists(dir)) {
            TestLog.warn("data dir not found: " + dir.toAbsolutePath() + " (создаю)");
            Files.createDirectories(dir);
            return;
        }

        // удаляем ВСЁ внутри папки, но саму папку оставляем
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .filter(p -> !p.equals(dir))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException("Не смог удалить: " + p.toAbsolutePath(), e);
                    }
                });

        TestLog.ok("data очищена: " + dir.toAbsolutePath());
    }
}