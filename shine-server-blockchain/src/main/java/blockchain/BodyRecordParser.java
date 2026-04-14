package blockchain;

import blockchain.body.*;

/**
 * Parser for body record by header type/subType/version.
 */
public final class BodyRecordParser {

    private BodyRecordParser() {}

    public static BodyRecord parse(short type, short subType, short version, byte[] bodyBytes) {
        if (bodyBytes == null) throw new IllegalArgumentException("bodyBytes == null");

        int t = type & 0xFFFF;
        int v = version & 0xFFFF;
        int st = subType & 0xFFFF;

        // TECH supports Header v1 and CreateChannel v1/v2.
        if (t == (CreateChannelBody.TYPE & 0xFFFF)) {
            if (st == (HeaderBody.SUBTYPE_COMPAT & 0xFFFF) && v == (HeaderBody.VER & 0xFFFF)) {
                return new HeaderBody(subType, version, bodyBytes).check();
            }
            if (st == (CreateChannelBody.SUBTYPE & 0xFFFF)
                    && (v == (CreateChannelBody.VER & 0xFFFF) || v == (CreateChannelBody.VER2 & 0xFFFF))) {
                return new CreateChannelBody(subType, version, bodyBytes).check();
            }
            throw new IllegalArgumentException(
                    String.format("Unknown TECH body type/version/subType: type=%d ver=%d subType=%d", t, v, st)
            );
        }

        int key = (t << 16) | v;

        BodyRecord r = switch (key) {
            case TextBody.KEY -> {
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

            case ReactionBody.KEY -> new ReactionBody(subType, version, bodyBytes);
            case ConnectionBody.KEY -> new ConnectionBody(subType, version, bodyBytes);
            case UserParamBody.KEY -> new UserParamBody(subType, version, bodyBytes);

            default -> throw new IllegalArgumentException(String.format(
                    "Unknown body type/version from header: type=%d ver=%d subType=%d",
                    t, v, st
            ));
        };

        return r.check();
    }
}
