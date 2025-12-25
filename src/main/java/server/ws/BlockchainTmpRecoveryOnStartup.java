package server.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shine.db.dao.BlockchainStateDAO;
import shine.db.entities.BlockchainStateEntry;
import utils.files.FileStoreUtil;
import shine.log.BlockchainAdminNotifier;

import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ===============================================================
 * BlockchainTmpRecoveryOnStartup — восстановление консистентности
 * blockchain файлов при старте сервера.
 *
 * Сценарий проблемы:
 *  - при добавлении блока сначала пишется <name>.tmp_bch
 *  - потом коммитится БД (state.fileSizeBytes)
 *  - потом tmp переименовывается поверх <name>.bch (атомарно, если возможно)
 *
 * Если сервер упал в середине, может остаться tmp:
 *  - tmp есть, а основной .bch остался старым
 *  - tmp есть, а основной .bch уже удалили/заменить не успели
 *  - tmp есть, а БД успела/не успела обновиться
 *
 * Этот класс при старте:
 *  - ищет все *.tmp_bch в data/
 *  - сравнивает размеры:
 *      - tmp
 *      - main (если есть)
 *      - state.fileSizeBytes (если есть)
 *
 * Правила:
 *
 * A) state есть:
 *   - если stateSize == mainSize => tmp удаляем
 *   - если stateSize == tmpSize  => tmp ставим на место main (atomicReplaceBlockchainFile)
 *   - иначе => КРИТИЧЕСКАЯ ОШИБКА: сервер останавливаем + уведомление администратору
 *
 * B) state НЕТ:
 *   - если main НЕТ и tmp ЕСТЬ => tmp удаляем (мусор после падения/неуспешной транзакции)
 *   - если main ЕСТЬ и tmp ЕСТЬ => КРИТИЧЕСКАЯ ОШИБКА: уведомление администратору + стоп сервера
 *
 * Логирование:
 *  - обо всех восстановленных/удалённых tmp пишем в лог
 *  - если tmp-файлов нет — тоже пишем в лог
 * ===============================================================
 */
public final class BlockchainTmpRecoveryOnStartup {

    private static final Logger log = LoggerFactory.getLogger(BlockchainTmpRecoveryOnStartup.class);

    private BlockchainTmpRecoveryOnStartup() {}

    /**
     * Запуск восстановления.
     * Если обнаружена ситуация, когда размеры не совпали и сервер сам не может чинить — бросаем исключение.
     */
    public static void runRecoveryOrThrow() {
        FileStoreUtil fs = FileStoreUtil.getInstance();
        BlockchainStateDAO stateDAO = BlockchainStateDAO.getInstance();

        Path dataDir = Paths.get(FileStoreUtil.DATA_DIR_NAME);
        ensureDirExists(dataDir);

        List<Path> tmpFiles = listTmpFiles(dataDir);

        if (tmpFiles.isEmpty()) {
            log.info("🟢 BlockchainTmpRecovery: временных *.tmp_bch файлов не найдено — восстановление не требуется.");
            return;
        }

        log.warn("🟡 BlockchainTmpRecovery: найдено временных файлов: {}", tmpFiles.size());

        for (Path tmpPath : tmpFiles) {
            String fileName = tmpPath.getFileName().toString();
            String blockchainName = extractBlockchainNameFromTmp(fileName);

            if (blockchainName == null || blockchainName.isBlank()) {
                // странное имя — не трогаем автоматически, но это уже повод дернуть админа
                BlockchainAdminNotifier.critical(
                        "НАЙДЕН TMP-ФАЙЛ С НЕОЖИДАННЫМ ИМЕНЕМ: " + fileName + " (не могу определить blockchainName).",
                        null
                );
                throw new IllegalStateException("Bad tmp file name: " + fileName);
            }

            Path mainPath = dataDir.resolve(fs.buildBlockchainFileName(blockchainName));

            long tmpSize = safeSize(tmpPath);
            boolean mainExists = Files.exists(mainPath);
            long mainSize = mainExists ? safeSize(mainPath) : -1L;

            BlockchainStateEntry st = null;
            try {
                st = stateDAO.getByBlockchainName(blockchainName);
            } catch (SQLException e) {
                BlockchainAdminNotifier.critical(
                        "ОШИБКА БД ПРИ ВОССТАНОВЛЕНИИ TMP: blockchainName=" + blockchainName + " (сервер остановлен).",
                        e
                );
                throw new IllegalStateException("DB error during tmp recovery for " + blockchainName, e);
            }

            // ============================================================
            // CASE B) state НЕТ
            // ============================================================
            if (st == null) {

                if (!mainExists) {
                    // НЕТ state, НЕТ main, есть tmp => удаляем tmp
                    log.warn("🟠 BlockchainTmpRecovery: state отсутствует и main отсутствует, но tmp найден => удаляем tmp. blockchainName={}, tmpSize={}",
                            blockchainName, tmpSize);
                    safeDelete(tmpPath);
                    continue;
                }

                // НЕТ state, но main есть и tmp есть => это уже подозрительно
                BlockchainAdminNotifier.critical(
                        "НЕСОГЛАСОВАННОСТЬ: ЕСТЬ main И tmp, НО НЕТ state В БД. " +
                                "blockchainName=" + blockchainName +
                                ", mainSize=" + mainSize +
                                ", tmpSize=" + tmpSize +
                                ". СЕРВЕР ОСТАНОВЛЕН. " +
                                "ПОДОЗРЕНИЕ: файлы могли быть изменены вне сервера.",
                        null
                );
                throw new IllegalStateException("State missing but both main and tmp exist for " + blockchainName);
            }

            // ============================================================
            // CASE A) state ЕСТЬ
            // ============================================================
            long stateSize = st.getFileSizeBytes();

            // 1) stateSize == mainSize => tmp мусор
            if (mainExists && mainSize == stateSize) {
                log.info("🟢 BlockchainTmpRecovery: stateSize совпадает с main => tmp удаляем. blockchainName={}, stateSize={}, mainSize={}, tmpSize={}",
                        blockchainName, stateSize, mainSize, tmpSize);
                safeDelete(tmpPath);
                continue;
            }

            // 2) stateSize == tmpSize => tmp это актуальная версия, ставим на место main
            if (tmpSize == stateSize) {
                log.warn("🟡 BlockchainTmpRecovery: stateSize совпадает с tmp => восстанавливаем main из tmp. blockchainName={}, stateSize={}, mainSize={}, tmpSize={}",
                        blockchainName, stateSize, mainSize, tmpSize);

                try {
                    // метод уже есть и делает move tmp->main с попыткой ATOMIC_MOVE
                    fs.atomicReplaceBlockchainFile(blockchainName);

                    // после move tmp должен исчезнуть сам (перемещён)
                    log.info("✅ BlockchainTmpRecovery: восстановление выполнено. blockchainName={}, newMainSize={}",
                            blockchainName, safeSize(mainPath));

                } catch (Exception e) {
                    BlockchainAdminNotifier.critical(
                            "НЕ УДАЛОСЬ ВОССТАНОВИТЬ main ИЗ tmp (move failed). " +
                                    "blockchainName=" + blockchainName +
                                    ", stateSize=" + stateSize +
                                    ", mainSize=" + mainSize +
                                    ", tmpSize=" + tmpSize +
                                    ". СЕРВЕР ОСТАНОВЛЕН.",
                            e
                    );
                    throw new IllegalStateException("Cannot replace main from tmp for " + blockchainName, e);
                }
                continue;
            }

            // 3) НИЧЕГО НЕ СОВПАЛО => критическая ситуация
            BlockchainAdminNotifier.critical(
                    "ФАТАЛЬНАЯ НЕСОГЛАСОВАННОСТЬ BLOCKCHAIN ФАЙЛОВ. " +
                            "blockchainName=" + blockchainName +
                            ", stateSize=" + stateSize +
                            ", mainExists=" + mainExists +
                            ", mainSize=" + mainSize +
                            ", tmpSize=" + tmpSize +
                            ". СЕРВЕР ОСТАНОВЛЕН. " +
                            "ТУТ НУЖНО УВЕДОМЛЕНИЕ АДМИНИСТРАТОРУ: возможно файлы изменены вручную/другой программой.",
                    null
            );
            throw new IllegalStateException("Blockchain files mismatch for " + blockchainName);
        }

        log.info("✅ BlockchainTmpRecovery: обработка tmp-файлов завершена.");
    }

    /* ===================================================================== */
    /* =============================== Helpers ============================== */
    /* ===================================================================== */

    private static void ensureDirExists(Path dir) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create data dir: " + dir, e);
        }
    }

    private static List<Path> listTmpFiles(Path dataDir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dataDir, "*" + FileStoreUtil.BLOCKCHAIN_TMP_EXTENSION)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) out.add(p);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list tmp files in: " + dataDir, e);
        }
        return out;
    }

    /**
     * Из "anya0001.tmp_bch" -> "anya0001"
     */
    private static String extractBlockchainNameFromTmp(String tmpFileName) {
        if (tmpFileName == null) return null;
        if (!tmpFileName.endsWith(FileStoreUtil.BLOCKCHAIN_TMP_EXTENSION)) return null;

        String base = tmpFileName.substring(0, tmpFileName.length() - FileStoreUtil.BLOCKCHAIN_TMP_EXTENSION.length());

        // базовая защита: не допускаем слэши/.. даже если кто-то подложил файл
        if (base.isBlank()) return null;
        if (base.contains("/") || base.contains("\\") || base.contains("..")) return null;

        return base;
    }

    private static long safeSize(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read file size: " + p, e);
        }
    }

    private static void safeDelete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete file: " + p, e);
        }
    }
}