package utils.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BchInfoManager — Singleton.
 *.
 * Держит в памяти информацию обо всех блокчейнах.
 * Сейчас читает и пишет JSON на диск (data/blockchain_info.json).
 * В будущем можно заменить на SQL без изменений в остальном коде.
 */
public final class BchInfoManager {

    private static final Logger log = LoggerFactory.getLogger(BchInfoManager.class);

    private static final String FILE_NAME = "blockchain_info.json";
    private static final Path   DATA_DIR  = Paths.get("data");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static BchInfoManager instance;

    /** blockchainId → запись о цепочке */
    private final Map<Long, BchInfoEntry> records = new LinkedHashMap<>();
    private final Path path = DATA_DIR.resolve(FILE_NAME);

    private BchInfoManager() {
        ensureDataDir();
        load();
    }

    public static synchronized BchInfoManager getInstance() {
        if (instance == null) instance = new BchInfoManager();
        return instance;
    }

    // ========== API ==========

    /** Создать новую цепочку (после первого HEADER-блока). */
    public synchronized void addBlockchain(long blockchainId,
                                           String userLogin,
                                           byte[] publicKey32,
                                           int blockchainSizeLimit) {
        if (publicKey32 == null || publicKey32.length != 32)
            throw new IllegalArgumentException("publicKey32 must be 32 bytes");
        if (records.containsKey(blockchainId))
            throw new IllegalArgumentException("blockchain already exists: " + blockchainId);

        BchInfoEntry entry = new BchInfoEntry(
                blockchainId,
                userLogin,
                Base64.getEncoder().encodeToString(publicKey32),
                blockchainSizeLimit,
                0, 0, ""
        );

        records.put(blockchainId, entry);
        log.info("Добавлен блокчейн id={} login='{}' (лимит {})", blockchainId, userLogin, blockchainSizeLimit);
        save();
    }

    /** Обновить состояние после добавления нового блока. */
    public synchronized void updateBlockchainState(long blockchainId,
                                                   int lastBlockNumber,
                                                   String lastBlockHash,
                                                   int blockchainSize) {
        BchInfoEntry prev = getEntryOrThrow(blockchainId);

        BchInfoEntry updated = new BchInfoEntry(
                prev.blockchainId,
                prev.userLogin,
                prev.publicKeyBase64,
                prev.blockchainSizeLimit,
                blockchainSize,
                lastBlockNumber,
                lastBlockHash
        );

        records.put(blockchainId, updated);
        log.info("Обновлено состояние id={} lastNum={} hash={} size={}",
                blockchainId, lastBlockNumber, lastBlockHash, blockchainSize);
        save();
    }

    /** Получить полную информацию по blockchainId. */
    public synchronized BchInfoEntry getBchInfo(long blockchainId) {
        return records.get(blockchainId);
    }

    /** Быстро проверить существование цепочки. */
    public synchronized boolean exists(long blockchainId) {
        return records.containsKey(blockchainId);
    }

    /** id → userLogin (для поиска пользователей). */
    public synchronized Map<Long, String> getAllLoginsSnapshot() {
        Map<Long, String> copy = new LinkedHashMap<>(records.size());
        for (var e : records.entrySet()) {
            copy.put(e.getKey(), e.getValue().userLogin);
        }
        return copy;
    }

    // ========== private ==========

    private BchInfoEntry getEntryOrThrow(long blockchainId) {
        BchInfoEntry e = records.get(blockchainId);
        if (e == null) throw new IllegalStateException("Блокчейн с id=" + blockchainId + " не найден.");
        return e;
    }

    private void ensureDataDir() {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
                log.info("Создана директория данных: {}", DATA_DIR);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать директорию хранения: " + DATA_DIR, e);
        }
    }

    private synchronized void load() {
        if (!Files.exists(path)) {
            save();
            log.info("Создан JSON-хранилище: {}", path);
            return;
        }
        try {
            byte[] json = Files.readAllBytes(path);
            if (json.length == 0) return;

            Map<String, BchInfoEntry> map = MAPPER.readValue(
                    json,
                    MAPPER.getTypeFactory().constructMapType(Map.class, String.class, BchInfoEntry.class)
            );

            records.clear();
            for (var e : map.entrySet()) {
                try {
                    long id = Long.parseLong(e.getKey());
                    records.put(id, e.getValue());
                } catch (NumberFormatException nfe) {
                    log.warn("Пропущен некорректный ключ '{}' в JSON", e.getKey());
                }
            }
            log.info("Загружено {} записей из {}", records.size(), path);
        } catch (IOException e) {
            log.error("Ошибка загрузки {}", path, e);
        }
    }

    /** Атомарная запись JSON через .tmp + ATOMIC_MOVE */
    private synchronized void save() {
        try {
            Map<String, BchInfoEntry> map = new LinkedHashMap<>();
            for (var e : records.entrySet())
                map.put(String.valueOf(e.getKey()), e.getValue());

            byte[] json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(map);

            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.write(tmp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            log.debug("Сохранено {} записей в {}", records.size(), path);
        } catch (IOException e) {
            log.error("Ошибка сохранения {}", path, e);
        }
    }
}
