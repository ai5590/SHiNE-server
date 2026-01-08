package test.it.runner;

import test.it.utils.TestConfig;
import test.it.utils.log.TestLog;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

/**
 *
 * Делает:
 *  1) чистит папку data/
 */
public class IT_CleanAllDate {

    private static final String DATA_DIR = "data";

    public static void main(String[] args) {
//        ItRunContext.initIfNeeded();

        TestLog.title("IT RUN CLEAN: очистка data/ + запуск всех тестов");

        try {
            cleanupDataDir(DATA_DIR);
        } catch (Throwable t) {
            TestLog.boom("Не смог очистить data/. Причина: " + t.getMessage());
            if (TestConfig.DEBUG()) t.printStackTrace(System.out);
            System.exit(1);
        }

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