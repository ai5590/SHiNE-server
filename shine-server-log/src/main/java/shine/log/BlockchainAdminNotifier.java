package shine.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ===============================================================
 * BlockchainAdminNotifier — уведомления администратору о критических
 * ошибках консистентности блокчейн-файлов.
 *
 * Сейчас:
 *  - пишет МАКСИМАЛЬНО ЗАМЕТНЫЙ лог
 *
 * TODO:
 *  - реальная отправка уведомления администратору:
 *      * Telegram bot / email / SMS / webhook / Sentry / PagerDuty
 *      * желательно с hostname, временем, именем блокчейна, размерами и stacktrace
 * ===============================================================
 */
public final class BlockchainAdminNotifier {

    private static final Logger log = LoggerFactory.getLogger(BlockchainAdminNotifier.class);

    private BlockchainAdminNotifier() {}

    public static void critical(String message) {
        critical(message, null);
    }

    public static void critical(String message, Throwable t) {

        String bannerTop =
                "\n" +
                "=================================================================\n" +
                "====================  !!! КРИТИЧЕСКАЯ ОШИБКА !!!  ===============\n" +
                "=================================================================";

        String bannerBottom =
                "=================================================================\n" +
                "====================  !!! НУЖНО ВМЕШАТЕЛЬСТВО !!! ===============\n" +
                "=================================================================\n";

        if (t == null) {
            log.error("{}\n{}\n{}",
                    bannerTop,
                    message,
                    bannerBottom
            );
        } else {
            log.error("{}\n{}\n{}",
                    bannerTop,
                    message,
                    bannerBottom,
                    t
            );
        }

        // TODO: Реальная отправка уведомления администратору (Telegram/email/webhook/Sentry)
    }
}