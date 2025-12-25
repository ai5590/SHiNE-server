package utils.files;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;

/**
 * ===============================================================
 *  FileStoreUtil — утилита работы с файлами в папке data/.
 *
 *  Теперь поддерживает:
 *   - основной файл блокчейна:   <blockchainName>.bch
 *   - временный файл блокчейна:  <blockchainName>.tmp_bch
 *
 *  Важное:
 *   - validateSimpleFileName() запрещает path traversal.
 *   - atomicReplaceBlockchainFile(): пытается сделать ATOMIC_MOVE (если ФС поддерживает),
 *     иначе делает обычный REPLACE_EXISTING move.
 * ===============================================================
 */
public final class FileStoreUtil {

    /** Базовая папка для хранения всех файлов (создаётся автоматически). */
    public static final String DATA_DIR_NAME = "data";

    /** Расширение основного файла блокчейна. */
    public static final String BLOCKCHAIN_FILE_EXTENSION = ".bch";

    /** Расширение временного файла (старое+новое). */
    public static final String BLOCKCHAIN_TMP_EXTENSION = ".tmp_bch";

    private static final FileStoreUtil INSTANCE = new FileStoreUtil();

    private final Path dataDirPath;

    private FileStoreUtil() {
        this.dataDirPath = Paths.get(DATA_DIR_NAME);
        ensureDataDirExists();
    }

    public static FileStoreUtil getInstance() {
        return INSTANCE;
    }

    /* ===================================================================== */
    /* ======================== Базовые операции =========================== */
    /* ===================================================================== */

    public void newFile(String fileName, byte[] data) {
        Objects.requireNonNull(data, "data == null");
        Path target = resolveSafe(fileName);
        try {
            Files.write(target, data,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось записать файл: " + target, e);
        }
    }

    public void addDataToFile(String fileName, byte[] data) {
        Objects.requireNonNull(data, "data == null");
        Path target = resolveSafe(fileName);
        try {
            Files.write(target, data,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось дописать файл: " + target, e);
        }
    }

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

    public boolean exists(String fileName) {
        Path target = resolveSafe(fileName);
        return Files.exists(target);
    }

    public long size(String fileName) {
        Path target = resolveSafe(fileName);
        try {
            return Files.size(target);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось получить размер файла: " + target, e);
        }
    }

    /* ===================================================================== */
    /* ===================== Блокчейн-файлы по имени ======================= */
    /* ===================================================================== */

    /** <blockchainName>.bch */
    public String buildBlockchainFileName(String blockchainName) {
        validateSimpleFileName(blockchainName);
        return blockchainName + BLOCKCHAIN_FILE_EXTENSION;
    }

    /** <blockchainName>.tmp_bch */
    public String buildBlockchainTmpFileName(String blockchainName) {
        validateSimpleFileName(blockchainName);
        return blockchainName + BLOCKCHAIN_TMP_EXTENSION;
    }

    public Path resolveBlockchainPath(String blockchainName) {
        return resolveSafe(buildBlockchainFileName(blockchainName));
    }

    public Path resolveBlockchainTmpPath(String blockchainName) {
        return resolveSafe(buildBlockchainTmpFileName(blockchainName));
    }

    public byte[] readBlockchain(String blockchainName) {
        return readAllDataFromFile(buildBlockchainFileName(blockchainName));
    }

    public void writeBlockchainTmp(String blockchainName, byte[] data) {
        newFile(buildBlockchainTmpFileName(blockchainName), data);
    }

    /**
     * Атомарно заменить основной файл блокчейна временным:
     *   <name>.tmp_bch  ->  <name>.bch
     *
     * Стратегия:
     *  1) Пытаемся Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)
     *  2) Если ATOMIC_MOVE не поддерживается — делаем move с REPLACE_EXISTING без атомарности
     *
     * Важный нюанс:
     *  - атомарность гарантируется только в пределах одной файловой системы.
     */
    public void atomicReplaceBlockchainFile(String blockchainName) {
        Path tmp = resolveBlockchainTmpPath(blockchainName);
        Path main = resolveBlockchainPath(blockchainName);

        if (!Files.exists(tmp)) {
            throw new IllegalStateException("TMP-файл не найден: " + tmp);
        }

        try {
            // 1) Пытаемся атомарный move
            Files.move(tmp, main,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 2) Если ФС не поддерживает атомарный move — делаем обычный replace
            try {
                Files.move(tmp, main, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new IllegalStateException("Не удалось заменить файл блокчейна (non-atomic): " + main, ex);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось заменить файл блокчейна (atomic): " + main, e);
        }
    }

    /* ===================================================================== */
    /* ============================ Helpers ================================= */
    /* ===================================================================== */

    private void ensureDataDirExists() {
        try {
            if (!Files.exists(dataDirPath)) {
                Files.createDirectories(dataDirPath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию хранения: " + dataDirPath, e);
        }
    }

    private Path resolveSafe(String fileName) {
        validateSimpleFileName(fileName);
        return dataDirPath.resolve(fileName);
    }

    /**
     * Валидация "простого имени":
     *  - запрещаем слэши, обратные слэши, ".."
     *  - запрещаем пустоту
     *
     * Важно: сюда у нас попадает и blockchainName (как часть имени файла),
     * поэтому blockchainName должен быть "простым": без путей.
     */
    private void validateSimpleFileName(String fileName) {
        Objects.requireNonNull(fileName, "fileName == null");
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла не должно быть пустым");
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Недопустимое имя файла: " + fileName);
        }
    }
}