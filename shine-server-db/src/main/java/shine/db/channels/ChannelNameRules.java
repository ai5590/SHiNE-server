package shine.db.channels;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ChannelNameRules {
    private static final int MIN_DISPLAY_NAME_LENGTH = 3;
    private static final int MAX_DISPLAY_NAME_LENGTH = 32;
    private static final Pattern DISPLAY_ALLOWED_PATTERN =
            Pattern.compile("^[\\p{IsLatin}\\p{IsCyrillic}0-9 _-]+$");

    private ChannelNameRules() {}

    public static String normalizeDisplayName(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    public static String requireValidDisplayNameForCreate(String rawName) {
        String normalized = normalizeDisplayName(rawName);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("channelName is blank");
        }

        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_DISPLAY_NAME_LENGTH || length > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("channelName length must be 3..32");
        }

        if (!DISPLAY_ALLOWED_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("channelName contains unsupported characters");
        }

        return normalized;
    }

    public static String toCanonicalSlug(String rawName) {
        String normalized = normalizeDisplayName(rawName);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("channelName is blank");
        }

        String lowered = normalized.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
        StringBuilder slug = new StringBuilder(lowered.length());
        boolean pendingSeparator = false;

        for (int i = 0; i < lowered.length(); ) {
            int cp = lowered.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == ' ' || cp == '_' || cp == '-') {
                pendingSeparator = slug.length() > 0;
                continue;
            }

            if (!isLatinOrCyrillicOrDigit(cp)) {
                throw new IllegalArgumentException("channelName contains unsupported characters");
            }

            if (pendingSeparator && slug.length() > 0) {
                slug.append('-');
            }
            pendingSeparator = false;
            slug.appendCodePoint(cp);
        }

        int len = slug.length();
        if (len > 0 && slug.charAt(len - 1) == '-') {
            slug.deleteCharAt(len - 1);
        }

        if (slug.length() == 0) {
            throw new IllegalArgumentException("channelName canonical slug is empty");
        }

        return slug.toString();
    }

    private static boolean isLatinOrCyrillicOrDigit(int cp) {
        if (Character.isDigit(cp)) return true;
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.LATIN || script == Character.UnicodeScript.CYRILLIC;
    }
}
