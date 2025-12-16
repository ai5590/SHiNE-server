package utils.files;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * ===============================================================
 *  FileStoreUtilSelfTest — запускаемый тест утилиты FileStoreUtil.
 *  ---------------------------------------------------------------
 *  Сценарий:
 *    1) Создаём «блокчейн-файл» для id=20251021 с начальными данными.
 *    2) Дозаписываем ещё порцию данных.
 *    3) Читаем целиком и печатаем длину + превью.
 *.
 *  Ожидаемый итог:
 *    • В папке "data" появится файл "20251021.bch"
 *    • В консоли будет длина содержимого и небольшой превью-дамп.
 * ===============================================================
 */
public class FileStoreUtilSelfTest {

    public static void main(String[] args) {
        System.out.println("=== FileStoreUtil self-test ===");

        FileStoreUtil fs = FileStoreUtil.getInstance();

        long blockchainId = 20251021L;

        byte[] part1 = "Hello ".getBytes(StandardCharsets.UTF_8);
        byte[] part2 = "Blockchain!".getBytes(StandardCharsets.UTF_8);

        // 1) создаём новый файл для «блокчейна»
        fs.newBlockchain(blockchainId, part1);

        // 2) дозаписываем данные
        fs.addDataToBlockchain(blockchainId, part2);

        // 3) читаем всё содержимое и показываем превью
        byte[] all = fs.readAllDataFromBlockchain(blockchainId);
        System.out.println("Total bytes read: " + all.length);
        System.out.println("Preview (UTF-8): " + new String(all, StandardCharsets.UTF_8));

        // небольшой hex-дамп первых 32 байт (для наглядности)
        System.out.println("Preview (HEX 32B): " + toHexPreview(all, 32));

        System.out.println("✅ Self-test passed (файл: data/" + blockchainId + FileStoreUtil.BLOCKCHAIN_FILE_EXTENSION + ")");
    }

    private static String toHexPreview(byte[] data, int max) {
        int n = Math.min(data.length, max);
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02X", data[i]));
            if (i + 1 < n) sb.append(' ');
        }
        if (data.length > n) sb.append(" ...");
        return sb.toString();
    }
}
