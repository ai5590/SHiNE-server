package server.logic.ws_protocol.JSON.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class NetIdGenerator {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private NetIdGenerator() {}

    public static String eventId(String prefix) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return (prefix == null || prefix.isBlank() ? "evt" : prefix)
                + "-" + now.format(FMT)
                + "-" + randomSuffix(10);
    }

    private static String randomSuffix(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
