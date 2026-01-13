// =======================
// blockchain/body/BodyRecordParser.java   (ИЗМЕНЁННЫЙ под новый формат)
// =======================
package blockchain.body;

/**
 * Парсер body теперь выбирает класс по header: type/subType/version,
 * потому что bodyBytes больше НЕ содержат type/subType/version.
 */
public final class BodyRecordParser {

    private BodyRecordParser() {}

    public static BodyRecord parse(short type, short subType, short version, byte[] bodyBytes) {
        if (bodyBytes == null) throw new IllegalArgumentException("bodyBytes == null");

        int t = type & 0xFFFF;
        int v = version & 0xFFFF;

        // ключ = (type<<16)|version (как раньше по смыслу), но берём из HEADER
        int key = (t << 16) | v;

        return switch (key) {
            case HeaderBody.KEY     -> new HeaderBody(subType, version, bodyBytes);
            case TextBody.KEY       -> new TextBody(subType, version, bodyBytes);
            case ReactionBody.KEY   -> new ReactionBody(subType, version, bodyBytes);
            case ConnectionBody.KEY -> new ConnectionBody(subType, version, bodyBytes);
            case UserParamBody.KEY  -> new UserParamBody(subType, version, bodyBytes);
            default -> throw new IllegalArgumentException(String.format(
                    "Unknown body type/version from header: type=%d ver=%d subType=%d",
                    t, v, (subType & 0xFFFF)
            ));
        };
    }
}