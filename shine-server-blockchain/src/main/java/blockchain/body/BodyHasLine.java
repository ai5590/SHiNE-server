package blockchain.body;

/**
 * BodyHasLine — для типов, которые имеют линейные поля в body.
 *
 * Line-prefix (BigEndian) в НАЧАЛЕ bodyBytes:
 *   [4]  lineCode                    код линии (root-идентификатор):
 *                                   - 0 для дефолтной линии/канала "0" (root = HEADER, blockNumber=0)
 *                                   - для канала "X": blockNumber root-блока канала (CREATE_CHANNEL)
 *
 *   [4]  prevLineBlockGlobalNumber   глобальный номер предыдущего блока в этой линии
 *   [32] prevLineBlockHash32         hash32 предыдущего блока в этой линии
 *
 *   [4]  lineSeq                     порядковый номер сообщения внутри линии (1..N)
 *
 * Важно:
 *  - Проверка связности линии (prevLineBlockGlobalNumber ↔ prevLineBlockHash32) и корректности lineSeq
 *    выполняется на сервере/в БД при вставке (а не в body.check()).
 */
public interface BodyHasLine {

    int lineCode();

    int prevLineBlockGlobalNumber();

    byte[] prevLineBlockHash32();

    int lineSeq();
}