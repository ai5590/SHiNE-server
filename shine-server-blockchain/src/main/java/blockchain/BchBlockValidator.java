package blockchain;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.blockchain.BchInfoEntry;
import utils.crypto.BchCryptoVerifier;

/**
 * BchBlockValidator — проверяет корректность блока:
 *   1) последовательность номеров блоков в цепочке;
 *   2) криптографическую целостность (подпись и хэш).
 *.
 * Не проверяет:
 *   - структуру и содержимое body;
 *   - поля HEADER и логин (этим занимаются другие проверки).
 */
public final class BchBlockValidator {

    private static final Logger log = LoggerFactory.getLogger(BchBlockValidator.class);

    private BchBlockValidator() {}

    /**
     * Проверяет, что блок может быть корректно добавлен к цепочке.
     *
     * Не используется при получении запроса на добавление блока по сети (тк там возвращаются более протоколо осмысленные коды
     * если блок не подходит по номеру.
     *
     * А этот класс может быть использован в будущем для внутренних, повторных проверок существующих цепочек блоков.
     *
     * @param block   блок (распарсенный из байт)
     * @param chain   информация о цепочке (BchInfoEntry)
     * @param chainId идентификатор цепочки
     * @return true если порядок и криптография корректны, иначе false
     */
    public static boolean validate(BchBlockEntry block, BchInfoEntry chain, long chainId) {
        if (block == null || chain == null) {
            log.warn("❌ Ошибка: блок или данные о цепочке не переданы");
            return false;
        }

        // 1️⃣ Проверка последовательности номера
        int expectedNumber = chain.lastBlockNumber + 1;
        if (block.recordNumber < expectedNumber) {
            log.warn("❌ Блок с номером {} уже существует (ожидался {})", block.recordNumber, expectedNumber);
            return false;
        }
        if (block.recordNumber > expectedNumber) {
            log.warn("❌ Нарушена последовательность: получен блок {}, ожидался {}", block.recordNumber, expectedNumber);
            return false;
        }

        // 2️⃣ Проверка публичного ключа
        byte[] publicKey = chain.getPublicKey32();
        if (publicKey == null || publicKey.length != 32) {
            log.warn("❌ В цепочке отсутствует корректный публичный ключ (chainId={})", chainId);
            return false;
        }

        // 3️⃣ Получаем предыдущий хэш
        byte[] prevHash32 = hexToBytes(chain.lastBlockHash);

        // 4️⃣ Проверка подписи и хэша
        try {
            boolean ok = BchCryptoVerifier.verifyAll(
                    chain.userLogin,
                    chainId,
                    prevHash32,
                    block.rawBytes,
                    block.getSignature64(),
                    block.getHash32(),
                    publicKey
            );

            if (!ok) {
                log.warn("❌ Криптографическая проверка не пройдена: хэш или подпись не совпадают (chainId={}, blockNum={})",
                        chainId, block.recordNumber);
                return false;
            }

            log.info("✅ Блок {} успешно прошёл проверку подписи и хэша (chainId={})",
                    block.recordNumber, chainId);
            return true;

        } catch (Exception e) {
            log.error("❌ Исключение при проверке блока (chainId={}): {}", chainId, e.getMessage());
            return false;
        }
    }

    // -------------------- HEX → байты --------------------

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[32]; // пустой хэш = 32 нуля
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }
}
