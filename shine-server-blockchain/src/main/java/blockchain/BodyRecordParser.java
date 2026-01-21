package blockchain;

import blockchain.body.*;

/**
 * Парсер body выбирает класс по header: type/subType/version,
 * потому что bodyBytes больше НЕ содержат type/subType/version.
 */
public final class BodyRecordParser {

    private BodyRecordParser() {}

    public static BodyRecord parse(short type, short subType, short version, byte[] bodyBytes) {
        if (bodyBytes == null) throw new IllegalArgumentException("bodyBytes == null");

        int t = type & 0xFFFF;
        int v = version & 0xFFFF;

        int key = (t << 16) | v;

        BodyRecord r = switch (key) {
            case HeaderBody.KEY -> {
                int st = subType & 0xFFFF;
                if (st == (HeaderBody.SUBTYPE_COMPAT & 0xFFFF)) {
                    yield new HeaderBody(subType, version, bodyBytes);
                }
                if (st == (CreateChannelBody.SUBTYPE & 0xFFFF)) {
                    yield new CreateChannelBody(subType, version, bodyBytes);
                }
                throw new IllegalArgumentException("Unknown TECH subType for type=0 ver=1: subType=" + st);
            }

            // TEXT type=1 ver=1: выбираем класс по subType
            case TextBody.KEY -> {
                int st = subType & 0xFFFF;

                if (st == (MsgSubType.TEXT_POST & 0xFFFF)
                        || st == (MsgSubType.TEXT_EDIT_POST & 0xFFFF)) {
                    yield new TextLineBody(subType, version, bodyBytes);
                }

                if (st == (MsgSubType.TEXT_REPLY & 0xFFFF)
                        || st == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF)) {
                    yield new TextReplyBody(subType, version, bodyBytes);
                }

                throw new IllegalArgumentException("Unknown TEXT subType for type=1 ver=1: subType=" + st);
            }

            case ReactionBody.KEY   -> new ReactionBody(subType, version, bodyBytes);
            case ConnectionBody.KEY -> new ConnectionBody(subType, version, bodyBytes);
            case UserParamBody.KEY  -> new UserParamBody(subType, version, bodyBytes);

            default -> throw new IllegalArgumentException(String.format(
                    "Unknown body type/version from header: type=%d ver=%d subType=%d",
                    t, v, (subType & 0xFFFF)
            ));
        };

        return r.check();
    }
}