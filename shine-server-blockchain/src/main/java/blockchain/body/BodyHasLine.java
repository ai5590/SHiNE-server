package blockchain.body;

/**
 * BodyHasLine — для типов, которые имеют линейные поля в body.
 *
 * В проекте hasLine встречается, например, у:
 *  - TECH: CREATE_CHANNEL (type=0, subType=1) — идёт по тех-линии
 *  - TEXT: POST / EDIT_POST (type=1, subType=10/11) — линия канала
 *  - CONNECTION (type=3)
 *  - USER_PARAM (type=4)
 *
 * Формат линейных полей (BigEndian) в НАЧАЛЕ bodyBytes:
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *
 * Важно:
 *  - Правильность prevLineNumber/hash и согласование thisLineNumber
 *    проверяется на сервере/в БД при вставке (а не в body.check()).
 */
public interface BodyHasLine {

    int prevLineNumber();

    byte[] prevLineHash32();

    int thisLineNumber();
}