package server.logic.ws_protocol.binary.handlers;

/**
 * Общий интерфейс для всех обработчиков входящих сообщений.
 */
public interface MessageHandler {
    /**
     * Обработать входящее сообщение и вернуть бинарный ответ.
     */
    byte[] handle(byte[] msg);
}
