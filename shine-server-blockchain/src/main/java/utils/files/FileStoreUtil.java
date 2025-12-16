package utils.files;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;

/**
 * ===============================================================
 *  FileStoreUtil — синглтон-утилита для записи/дозаписи/чтения файлов.
 *  ---------------------------------------------------------------
 *  Где хранит:
 *    • Все файлы размещаются в внешней папке DATA_DIR = "data" (в корне запуска).
 *      Папка создаётся автоматически при первом обращении.
 *.
 *  Что умеет:
 *    • newFile(String fileName, byte[] data)
 *        - создаёт/переписывает файл с именем fileName и записывает data.
 *    • addDataToFile(String fileName, byte[] data)
 *        - дописывает data в конец файла (создаст файл, если его ещё нет).
 *    • readAllDataFromFile(String fileName)
 *        - читает весь файл целиком и возвращает содержимое в виде byte[].
 *.
 *  Обёртки под «блокчейны»:
 *    • newBlockchain(long blockchainId, byte[] data)
 *    • addDataToBlockchain(long blockchainId, byte[] data)
 *    • readAllDataFromBlockchain(long blockchainId)
 *        - те же операции, но имя файла формируется из blockchainId и расширения ".bch".
 *.
 *  Безопасность имён:
 *    • Внутри утилиты есть простая валидация имени файла: запрещены разделители путей,
 *      чтобы исключить выход из каталога data (path traversal).
 *.
 *  Совместимость: Java 17.
 * ===============================================================
 */
public final class FileStoreUtil {

    /** Базовая папка для хранения всех файлов (создаётся автоматически). */
    public static final String DATA_DIR_NAME = "data";
    /** Расширение файлов «блокчейнов». */
    public static final String BLOCKCHAIN_FILE_EXTENSION = ".bch";

    private static final FileStoreUtil INSTANCE = new FileStoreUtil();

    private final Path dataDirPath;

    private FileStoreUtil() {
        this.dataDirPath = Paths.get(DATA_DIR_NAME);
        ensureDataDirExists();
    }

    /** Получить единственный экземпляр утилиты. */
    public static FileStoreUtil getInstance() {
        return INSTANCE;
    }

    // ===============================================================
    //                       ОБЩИЕ МЕТОДЫ РАБОТЫ С ФАЙЛОМ
    // ===============================================================

    /**
     * Создать/переписать файл и записать в него массив байт.
     * @param fileName имя файла (без каталогов)
     * @param data     содержимое
     * @throws IllegalArgumentException при неверном имени или null-данных
     * @throws IllegalStateException при ошибках ввода/вывода
     */
    public void newFile(String fileName, byte[] data) {
        Objects.requireNonNull(data, "Данные не должны быть null");
        Path target = resolveSafe(fileName);
        try {
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось записать файл: " + target, e);
        }
    }

    /**
     * Дозаписать массив байт в конец файла (создаст файл, если отсутствует).
     * @param fileName имя файла (без каталогов)
     * @param data     добавляемые данные
     * @throws IllegalArgumentException при неверном имени или null-данных
     * @throws IllegalStateException при ошибках ввода/вывода
     */
    public void addDataToFile(String fileName, byte[] data) {
        Objects.requireNonNull(data, "Данные не должны быть null");
        Path target = resolveSafe(fileName);
        try {
            Files.write(target, data,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось дописать файл: " + target, e);
        }
    }

    /**
     * Прочитать весь файл в память и вернуть как byte[].
     * @param fileName имя файла (без каталогов)
     * @return содержимое файла
     * @throws IllegalStateException если файл не существует или ошибка ввода/вывода
     */
    public byte[] readAllDataFromFile(String fileName) {
        Path target = resolveSafe(fileName);
        if (!Files.exists(target)) {
            throw new IllegalStateException("Файл не найден: " + target);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл: " + target, e);
        }
    }

    // ===============================================================
    //                  ОБЁРТКИ ДЛЯ «БЛОКЧЕЙН-ФАЙЛОВ»
    // ===============================================================

    /**
     * Обёртка над newFile: имя формируется из blockchainId + ".bch".
     */
    public void newBlockchain(long blockchainId, byte[] data) {
        String fileName = buildBlockchainFileName(blockchainId);
        newFile(fileName, data);
    }

    /**
     * Обёртка над addDataToFile: имя формируется из blockchainId + ".bch".
     */
    public void addDataToBlockchain(long blockchainId, byte[] data) {
        String fileName = buildBlockchainFileName(blockchainId);
        addDataToFile(fileName, data);
    }

    /**
     * Обёртка над readAllDataFromFile: имя формируется из blockchainId + ".bch".
     */
    public byte[] readAllDataFromBlockchain(long blockchainId) {
        String fileName = buildBlockchainFileName(blockchainId);
        return readAllDataFromFile(fileName);
    }

    // ===============================================================
    //                           ВСПОМОГАТЕЛЬНЫЕ
    // ===============================================================

    private void ensureDataDirExists() {
        try {
            if (!Files.exists(dataDirPath)) {
                Files.createDirectories(dataDirPath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию хранения: " + dataDirPath, e);
        }
    }

    /**
     * Безопасно собрать путь внутри каталога data, запретив подстановку каталогов в имени файла.
     */
    private Path resolveSafe(String fileName) {
        validateSimpleFileName(fileName);
        return dataDirPath.resolve(fileName);
    }

    /**
     * Простейшая валидация имени файла:
     *  • запретить разделители путей и возврат на родительский каталог.
     */
    private void validateSimpleFileName(String fileName) {
        Objects.requireNonNull(fileName, "Имя файла не должно быть null");
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла не должно быть пустым");
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Недопустимое имя файла: " + fileName);
        }
    }

    /**
     * Построить имя «блокчейн-файла» из идентификатора и расширения .bch.
     * Пример:  12345  →  "12345.bch"
     */
    private String buildBlockchainFileName(long blockchainId) {
        return Long.toString(blockchainId) + BLOCKCHAIN_FILE_EXTENSION;
    }
}
