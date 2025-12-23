//package server.logic.ws_protocol.binary.handlers;
//
//import blockchain.BchBlockEntry;
//import blockchain.body.BodyRecord;
//import blockchain.BodyRecordParser;
//import blockchain.body.HeaderBody;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import server.logic.ws_protocol.WireCodes;
//import utils.blockchain.BchInfoEntry;
//import utils.blockchain.BchInfoManager;
//import utils.crypto.BchCryptoVerifier;
//import utils.files.FileStoreUtil;
//
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.util.Arrays;
//
///**
// * AddBlockHandler — обработчик команды "добавить блок" (ADD_BLOCK)
// * ---------------------------------------------------------------
// * Принимает бинарное сообщение от клиента и добавляет новый блок в цепочку.
// *.
// * Формат входного сообщения (msg):
// *   [0..3]   — 4 байта: код операции (WireCodes.ADD_BLOCK)
// *   [4..11]  — 8 байт: blockchainId (уникальный идентификатор цепочки)
// *   [12..]   — байты полного блока .bch:
// *                 ├── 4  байта  recordSize = M + 18
// *                 ├── 4  байта  recordNumber
// *                 ├── 8  байт   timestamp
// *                 ├── 2  байта  recordType
// *                 ├── 2  байта  recordVersion
// *                 ├── M  байт   body (содержимое блока)
// *                 ├── 64 байта  signature (Ed25519)
// *                 └── 32 байта  hash (SHA-256)
// *.
// * ---------------------------------------------------------------
// * Алгоритм работы:
// *.
// * 1️⃣ Распаковать BchBlockEntry из msg (т.е. выделить тело блока и подписи).
// * 2️⃣ Найти описание цепочки (BchInfoEntry) по blockchainId.
// *.
// *   ─ Если описания нет (цепочка ещё не существует):
// *       • принимаем только блок типа 0 (HeaderBody) и номера 0;
// *       • парсим его, создаём новый BchInfoEntry на основе данных заголовка;
// *       • проверяем подпись и хэш;
// *       • проверяем корректность тела блока (check);
// *       • сохраняем блок и создаём новый blockchain-файл;
// *       • добавляем цепочку в менеджер BchInfoManager.
// *       (💡 временное решение: создание цепочки допустимо только через HeaderBody)
// *.
// *   ─ Если цепочка уже существует:
// *       • проверяем, что номер блока равен (lastBlockNumber + 1);
// *       • проверяем подпись и хэш;
// *       • проверяем тело блока (check);
// *       • добавляем блок в файл цепочки;
// *       • обновляем состояние BchInfoEntry (номер, хэш, размер).
// *.
// * 3️⃣ Если все проверки пройдены — возвращаем статус OK.
// *.
// * Таким образом, единственное различие между первым блоком и последующими —
// * момент инициализации описания цепочки (BchInfoEntry).
// * Всё остальное (валидация, подпись, добавление, обновление) выполняется одинаково.
// */
//public class AddBlockHandler implements MessageHandler {
//
//    private static final Logger log = LoggerFactory.getLogger(AddBlockHandler.class);
//
//    @Override
//    public byte[] handle(byte[] msg) {
//        try {
//            // =====================================================================
//            // 1️⃣ Проверка минимальной длины пакета
//            // =====================================================================
//            int minFull = BchBlockEntry.RAW_HEADER_SIZE + BchBlockEntry.SIGNATURE_LEN + BchBlockEntry.HASH_LEN;
//            // (RAW_HEADER_SIZE = 18 байт, подпись = 64, хэш = 32)
//            if (msg.length < 4 + 8 + minFull)
//                return code(WireCodes.Status.BAD_REQUEST);
//
//            // =====================================================================
//            // 2️⃣ Извлекаем blockchainId (8 байт начиная с позиции 4)
//            // =====================================================================
//            long blockchainId = ByteBuffer.wrap(msg, 4, 8)
//                    .order(ByteOrder.BIG_ENDIAN)
//                    .getLong();
//
//            // Всё, что дальше, — это бинарное содержимое блока .bch
//            int offset = 12; // первые 12 байт = код + blockchainId
//
//            // =====================================================================
//            // 3️⃣ Парсим блок (RAW + подпись + хэш)
//            // =====================================================================
//            byte[] fullBlock = Arrays.copyOfRange(msg, offset, msg.length);
//            BchBlockEntry block = new BchBlockEntry(fullBlock); // сам распакует RAW-часть и подписи
//
//            // =====================================================================
//            // 4️⃣ Получаем текущее описание цепочки (BchInfoEntry)
//            // =====================================================================
//            BchInfoManager info = BchInfoManager.getInstance();
//            BchInfoEntry chain = info.getBchInfo(blockchainId);
//
//            byte[] prevHash32;
//            int expectedNum;
//            String userLogin;
//            byte[] publicKey32;
//
//            // =====================================================================
//            // 🧩 СЦЕНАРИЙ 1: цепочка отсутствует — создаём новую
//            // =====================================================================
//            if (chain == null) {
//                // Допускаем только блок-заголовок (type=0, num=0)
//                if (block.recordType != BchBlockEntry.TYPE_HEADER || block.recordNumber != 0) {
//                    log.warn("Попытка создать новую цепочку без корректного заголовка (type={}, num={})",
//                            block.recordType, block.recordNumber);
//                    return code(WireCodes.Status.BAD_REQUEST);
//                }
//
//                // Парсим тело блока → HeaderBody
//                BodyRecord body = BodyRecordParser.parse(block.recordType, block.recordTypeVersion, block.body).check();
//                if (!(body instanceof HeaderBody))
//                    return code(WireCodes.Status.BAD_REQUEST);
//
//                HeaderBody hb = (HeaderBody) body;
//
//                // Проверяем, что blockchainId совпадает
//                if (hb.blockchainId != blockchainId) {
//                    log.warn("Несовпадение blockchainId в заголовке (ожидалось {}, получено {})",
//                            blockchainId, hb.blockchainId);
//                    return code(WireCodes.Status.BAD_REQUEST);
//                }
//
//                // Проверяем подпись и хэш первого блока (предыдущий хэш = 0)
//                prevHash32 = new byte[32];
//                boolean verified = BchCryptoVerifier.verifyAll(
//                        hb.userLogin,
//                        blockchainId,
//                        prevHash32,
//                        block.rawBytes,
//                        block.getSignature64(),
//                        block.getHash32(),
//                        hb.publicKey32
//                );
//                if (!verified) {
//                    log.warn("❌ Подпись не прошла проверку при создании цепочки blockchainId={}", blockchainId);
//                    return code(WireCodes.Status.UNVERIFIED);
//                }
//
//                // ✅ Всё хорошо: создаём новую цепочку
//                info.addBlockchain(blockchainId, hb.userLogin, hb.publicKey32, Integer.MAX_VALUE);
//                info.updateBlockchainState(blockchainId, block.recordNumber, bytesToHex(block.getHash32()), fullBlock.length);
//
//                FileStoreUtil.getInstance().addDataToBlockchain(blockchainId, fullBlock);
//
//                log.info("✅ Создана новая цепочка blockchainId={}, user={}, blockNum={}",
//                        blockchainId, hb.userLogin, block.recordNumber);
//
//                return code(WireCodes.Status.OK);
//            }
//
//            // =====================================================================
//            // 🧩 СЦЕНАРИЙ 2: цепочка существует — добавляем новый блок
//            // =====================================================================
//            expectedNum = chain.lastBlockNumber + 1;
//
//            // Проверка последовательности (и отправка lastBlockNumber)
//            if (block.recordNumber < expectedNum) {
//                log.info("🔁 Блок {} уже существует, последний = {}", block.recordNumber, chain.lastBlockNumber);
//                ByteBuffer out = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
//                out.putInt(WireCodes.Status.BLOCK_ALREADY_EXISTS);
//                out.putInt(chain.lastBlockNumber);
//                return out.array();
//            }
//            if (block.recordNumber > expectedNum) {
//                log.warn("⚠️ Нарушена последовательность: получен {}, ожидался {}", block.recordNumber, expectedNum);
//                ByteBuffer out = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
//                out.putInt(WireCodes.Status.OUT_OF_SEQUENCE);
//                out.putInt(chain.lastBlockNumber);
//                return out.array();
//            }
//
//            userLogin = chain.userLogin;
//            publicKey32 = chain.getPublicKey32();
//
//            // Хэш предыдущего блока (или 32 нуля, если это первый)
//            prevHash32 = (chain.lastBlockHash == null || chain.lastBlockHash.isEmpty())
//                    ? new byte[32]
//                    : hexToBytes(chain.lastBlockHash);
//
//            // Проверяем подпись и хэш
//            boolean verified = BchCryptoVerifier.verifyAll(
//                    userLogin,
//                    blockchainId,
//                    prevHash32,
//                    block.rawBytes,
//                    block.getSignature64(),
//                    block.getHash32(),
//                    publicKey32
//            );
//            if (!verified) {
//                log.warn("❌ Подпись не прошла проверку: chainId={}, blockNum={}", blockchainId, block.recordNumber);
//                return code(WireCodes.Status.UNVERIFIED);
//            }
//
//            // Проверяем тело блока (например, корректный UTF-8 или структура)
//            BodyRecord body = BodyRecordParser.parse(block.recordType, block.recordTypeVersion, block.body).check();
//
//            // ✅ Добавляем блок в файл цепочки
//            FileStoreUtil.getInstance().addDataToBlockchain(blockchainId, fullBlock);
//
//            // Обновляем состояние цепочки (номер, хэш, размер)
//            int newSize = chain.blockchainSize + fullBlock.length;
//            info.updateBlockchainState(blockchainId, block.recordNumber, bytesToHex(block.getHash32()), newSize);
//
//            log.info("✅ Блок добавлен: chain={}, num={}, type={}, bytes={}",
//                    blockchainId, block.recordNumber, block.recordType, fullBlock.length);
//
//            return code(WireCodes.Status.OK);
//
//        } catch (Exception e) {
//            log.error("❌ ADD_BLOCK: внутренняя ошибка при обработке", e);
//            return code(WireCodes.Status.INTERNAL_ERROR);
//        }
//    }
//
//    // =====================================================================
//    //                            Утилиты
//    // =====================================================================
//
//    /** Преобразовать статус (int) в 4 байта BigEndian. */
//    private static byte[] code(int status) {
//        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(status).array();
//    }
//
//    /** Конвертация HEX → bytes (для хэшей). */
//    private static byte[] hexToBytes(String hex) {
//        int len = hex.length();
//        byte[] out = new byte[len / 2];
//        for (int i = 0; i < len; i += 2)
//            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
//        return out;
//    }
//
//    /** Конвертация bytes → HEX (для сохранения в BchInfo). */
//    private static String bytesToHex(byte[] b) {
//        StringBuilder sb = new StringBuilder(b.length * 2);
//        for (byte x : b) sb.append(String.format("%02x", x));
//        return sb.toString();
//    }
//}
//
