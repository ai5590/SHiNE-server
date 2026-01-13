package blockchain.body;

/**
 * BodyHasLine — для типов, которые имеют линейные поля в body:
 * TEXT / CONNECTION / USER_PARAM
 *
 * Формат линейных полей (BigEndian) в НАЧАЛЕ bodyBytes:
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 */
public interface BodyHasLine {

    int prevLineNumber();

    byte[] prevLineHash32();

    int thisLineNumber();
}