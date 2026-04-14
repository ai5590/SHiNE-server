package shine.db;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseTriggersInstaller вЂ” СѓСЃС‚Р°РЅР°РІР»РёРІР°РµС‚ С‚СЂРёРіРіРµСЂС‹, РєРѕС‚РѕСЂС‹Рµ РїРѕРґРґРµСЂР¶РёРІР°СЋС‚ Р±РёР·РЅРµСЃ-Р»РѕРіРёРєСѓ Р‘Р”.
 *
 * РњС‹ СЃРїРµС†РёР°Р»СЊРЅРѕ СЃРґРµР»Р°Р»Рё С‚СЂРёРіРіРµСЂС‹ РјР°РєСЃРёРјР°Р»СЊРЅРѕ "СЃРѕРІРјРµСЃС‚РёРјС‹РјРё":
 *  - РќР•Рў РґРёРЅР°РјРёС‡РµСЃРєРёС… СЃРѕРѕР±С‰РµРЅРёР№ РІ RAISE(...): С‚РѕР»СЊРєРѕ С„РёРєСЃРёСЂРѕРІР°РЅРЅС‹Рµ СЃС‚СЂРѕРєРё.
 *    (РќРµРєРѕС‚РѕСЂС‹Рµ SQLite-СЃР±РѕСЂРєРё / РїСЂРѕСЃРјРѕС‚СЂС‰РёРєРё РїР°РґР°СЋС‚ РЅР° "||" РІРЅСѓС‚СЂРё RAISE.)
 *  - РќР•Рў UPSERT "ON CONFLICT DO UPDATE" вЂ” РІРјРµСЃС‚Рѕ РЅРµРіРѕ:
 *      INSERT OR IGNORE + UPDATE
 *    (РЎС‚Р°СЂС‹Рµ SQLite РЅРµ Р·РЅР°СЋС‚ UPSERT.)
 *
 * =============================================================================
 * РћРџРРЎРђРќРР• РўР РР“Р“Р•Р РћР’
 * =============================================================================
 *
 * [1] trg_blocks_line_integrity_bi  (BEFORE INSERT ON blocks)
 *     РљРѕРЅС‚СЂРѕР»СЊ С†РµР»РѕСЃС‚РЅРѕСЃС‚Рё "Р»РёРЅРёР№" (line_code / prev_line_number / prev_line_hash / this_line_number).
 *
 *     Р—Р°С‡РµРј СЌС‚Рѕ РЅСѓР¶РЅРѕ:
 *       - Р’ РєР°РЅР°Р»Р°С…/РІРµС‚РєР°С…/РґРµР№СЃС‚РІРёСЏС… С‚С‹ С…РѕС‡РµС€СЊ РёРјРµС‚СЊ "Р»РёРЅРµР№РЅСѓСЋ" РїРѕСЃР»РµРґРѕРІР°С‚РµР»СЊРЅРѕСЃС‚СЊ,
 *         РіРґРµ РєР°Р¶РґС‹Р№ СЃР»РµРґСѓСЋС‰РёР№ Р±Р»РѕРє СЏРІРЅРѕ СЃСЃС‹Р»Р°РµС‚СЃСЏ РЅР° РїСЂРµРґС‹РґСѓС‰РёР№ Р±Р»РѕРє Р»РёРЅРёРё
 *         Рё РїРѕРґС‚РІРµСЂР¶РґР°РµС‚, С‡С‚Рѕ СЃСЃС‹Р»РєР° РЅРµ РїРѕРґРјРµРЅРµРЅР°.
 *
 *     РљРѕРіРґР° СЃСЂР°Р±Р°С‚С‹РІР°РµС‚:
 *       - РўРћР›Р¬РљРћ РµСЃР»Рё РїСЂРё РІСЃС‚Р°РІРєРµ РїРµСЂРµРґР°РЅРѕ РҐРћРўРЇ Р‘Р« РћР”РќРћ РёР· line-РїРѕР»РµР№.
 *       - Р•СЃР»Рё line-РїРѕР»СЏ РЅРµ РїРµСЂРµРґР°РЅС‹ вЂ” С‚СЂРёРіРіРµСЂ РІРѕРѕР±С‰Рµ РЅРµ СЂР°Р±РѕС‚Р°РµС‚ (СЌС‚Рѕ РІР°Р¶РЅРѕ).
 *
 *     Р§С‚Рѕ РїСЂРѕРІРµСЂСЏРµС‚:
 *       A) line-РїРѕР»СЏ РґРѕРїСѓСЃРєР°СЋС‚СЃСЏ С‚РѕР»СЊРєРѕ РґР»СЏ msg_type:
 *          0 (TECH), 1 (TEXT), 3 (CONNECTION), 4 (USER_PARAM)
 *       B) Р•СЃР»Рё РїСЂРёС€Р»Рѕ С…РѕС‚СЊ РѕРґРЅРѕ line-РїРѕР»Рµ вЂ” РѕР±СЏР·Р°РЅС‹ РїСЂРёР№С‚Рё Р’РЎР• 4 (РЅРёРєР°РєРёС… "С‡Р°СЃС‚РёС‡РЅС‹С…")
 *       C) prev-Р±Р»РѕРє Р»РёРЅРёРё СЃСѓС‰РµСЃС‚РІСѓРµС‚ РІ С‚РѕР№ Р¶Рµ С†РµРїРѕС‡РєРµ bch_name
 *       D) prev_hash СЃРѕРІРїР°РґР°РµС‚ СЃ block_hash РЅР°Р№РґРµРЅРЅРѕРіРѕ prev-Р±Р»РѕРєР°
 *       E) line_code РєРѕСЂСЂРµРєС‚РЅС‹Р№:
 *          - Р»РёР±Рѕ РїРµСЂРІС‹Р№ С€Р°Рі РїРѕСЃР»Рµ root: prev_line_number == line_code
 *          - Р»РёР±Рѕ prev СѓР¶Рµ РїСЂРёРЅР°РґР»РµР¶РёС‚ СЌС‚РѕР№ Р»РёРЅРёРё: p.line_code == NEW.line_code
 *       F) this_line_number:
 *          - РїРµСЂРІС‹Р№ С€Р°Рі РїРѕСЃР»Рµ root:
 *              TEXT: this_line_number = 0
 *              TECH/CONNECTION/USER_PARAM: this_line_number = 1
 *          - РѕР±С‹С‡РЅС‹Р№ С€Р°Рі:
 *              TEXT: РґРѕРїСѓСЃРєР°РµРј same РёР»Рё +1 (С‡С‚РѕР±С‹ "edit" РјРѕРі РЅРµ РґРІРёРіР°С‚СЊ С€Р°Рі)
 *              TECH/CONNECTION/USER_PARAM: СЃС‚СЂРѕРіРѕ prev.this + 1
 *
 *     РљР°РєРёРµ РѕС€РёР±РєРё РєРёРґР°РµС‚:
 *       - LINE_ERR_UNSUPPORTED_TYPE_WITH_LINE
 *       - LINE_ERR_PARTIAL_FIELDS
 *       - LINE_ERR_NO_PREV
 *       - LINE_ERR_PREV_HASH_MISMATCH
 *       - LINE_ERR_LINE_CODE_MISMATCH
 *       - LINE_ERR_FIRST_STEP_BAD_THIS
 *       - LINE_ERR_THIS_LINE_BAD_STEP
 *
 * [2] trg_blocks_connection_state_ai  (AFTER INSERT ON blocks WHEN msg_type=3)
 *     РџРѕРґРґРµСЂР¶РёРІР°РµС‚ С‚Р°Р±Р»РёС†Сѓ connections_state РєР°Рє "С‚РµРєСѓС‰РµРµ СЃРѕСЃС‚РѕСЏРЅРёРµ" РѕС‚РЅРѕС€РµРЅРёР№:
 *       - FRIEND/CONTACT/FOLLOW  -> РґРѕР±Р°РІРёС‚СЊ/РѕР±РЅРѕРІРёС‚СЊ СЃРѕСЃС‚РѕСЏРЅРёРµ
 *       - UNFRIEND/UNCONTACT/UNFOLLOW -> СѓРґР°Р»РёС‚СЊ СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓСЋС‰РµРµ "РїРѕР·РёС‚РёРІРЅРѕРµ" СЃРѕСЃС‚РѕСЏРЅРёРµ
 *
 * [3] trg_blocks_message_stats_like_ai (AFTER INSERT ON blocks WHEN msg_type=2 AND sub_type=LIKE)
 *     РџРѕРґРґРµСЂР¶РёРІР°РµС‚ likes_count РІ message_stats РґР»СЏ С†РµР»Рё (to_*).
 *
 * [4] trg_blocks_message_stats_reply_ai (AFTER INSERT ON blocks WHEN msg_type=1 AND sub_type=REPLY)
 *     РџРѕРґРґРµСЂР¶РёРІР°РµС‚ replies_count РІ message_stats.
 *
 * [5] trg_blocks_edit_apply_ai (AFTER INSERT ON blocks WHEN msg_type=1 AND sub_type=EDIT)
 *     Р›РѕРіРёРєР° edit:
 *       - РїРѕРјРµС‡Р°РµС‚ РёСЃС…РѕРґРЅС‹Р№ Р±Р»РѕРє edited_by_block_number = NEW.block_number
 *       - СѓРІРµР»РёС‡РёРІР°РµС‚ edits_count РІ message_stats
 */
public final class DatabaseTriggersInstaller {

    private DatabaseTriggersInstaller() {}

    public static void createAllTriggers(Statement st) throws SQLException {
        dropTriggersByPrefix(st, "trg_blocks_");

        // РќР° РІСЃСЏРєРёР№ СЃР»СѓС‡Р°Р№ СѓР±РёСЂР°РµРј СЃС‚Р°СЂС‹Рµ "РєСЂРёРІРѕ РЅР°Р·РІР°РЅРЅС‹Рµ" С‚СЂРёРіРіРµСЂС‹,
        // РµСЃР»Рё РѕРЅРё РєРѕРіРґР°-С‚Рѕ РїРѕРїР°РґР°Р»Рё РІ Р‘Р”.
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_block_lini_integriti_by;");
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_line_integrity_bi;");

        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_connection_state_ai;");
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_message_stats_like_ai;");
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_message_stats_reply_ai;");
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_edit_apply_ai;");

        createLineIntegrityTrigger(st);
        createConnectionStateTrigger(st);
        createMessageStatsLikeTrigger(st);
        createMessageStatsReplyTrigger(st);
        createEditApplyTrigger(st);
    }

    private static void dropTriggersByPrefix(Statement st, String prefix) throws SQLException {
        List<String> triggerNames = new ArrayList<>();
        String sql = "SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE '" + prefix + "%'";
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && !name.isBlank()) {
                    triggerNames.add(name);
                }
            }
        }

        for (String name : triggerNames) {
            String safeName = name.replace("\"", "\"\"");
            st.executeUpdate("DROP TRIGGER IF EXISTS \"" + safeName + "\"");
        }
    }

    private static void createLineIntegrityTrigger(Statement st) throws SQLException {
        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_line_integrity_bi
            BEFORE INSERT ON blocks
            WHEN
                NEW.line_code IS NOT NULL
                OR NEW.prev_line_number IS NOT NULL
                OR NEW.prev_line_hash IS NOT NULL
                OR NEW.this_line_number IS NOT NULL
            BEGIN
                SELECT RAISE(ABORT, 'LINE_ERR_UNSUPPORTED_TYPE_WITH_LINE')
                WHERE NOT (NEW.msg_type IN (0, 1, 3, 4));

                SELECT RAISE(ABORT, 'LINE_ERR_PARTIAL_FIELDS')
                WHERE NEW.line_code IS NULL
                   OR NEW.prev_line_number IS NULL
                   OR NEW.prev_line_hash IS NULL
                   OR NEW.this_line_number IS NULL;

                SELECT RAISE(ABORT, 'LINE_ERR_NO_PREV')
                WHERE NOT EXISTS(
                    SELECT 1
                    FROM blocks p
                    WHERE p.bch_name = NEW.bch_name
                      AND p.block_number = NEW.prev_line_number
                    LIMIT 1
                );

                SELECT RAISE(ABORT, 'LINE_ERR_PREV_HASH_MISMATCH')
                WHERE NOT EXISTS(
                    SELECT 1
                    FROM blocks p
                    WHERE p.bch_name = NEW.bch_name
                      AND p.block_number = NEW.prev_line_number
                      AND p.block_hash = NEW.prev_line_hash
                    LIMIT 1
                );

                SELECT RAISE(ABORT, 'LINE_ERR_LINE_CODE_MISMATCH')
                WHERE NEW.prev_line_number <> NEW.line_code
                  AND NOT EXISTS(
                    SELECT 1
                    FROM blocks p
                    WHERE p.bch_name = NEW.bch_name
                      AND p.block_number = NEW.prev_line_number
                      AND p.line_code = NEW.line_code
                    LIMIT 1
                  );

                SELECT RAISE(ABORT, 'LINE_ERR_FIRST_STEP_BAD_THIS')
                WHERE NEW.prev_line_number = NEW.line_code
                  AND NEW.this_line_number <> (CASE WHEN NEW.msg_type = 1 THEN 0 ELSE 1 END);

                SELECT RAISE(ABORT, 'LINE_ERR_THIS_LINE_BAD_STEP')
                WHERE NEW.prev_line_number <> NEW.line_code
                  AND NOT EXISTS(
                    SELECT 1
                    FROM blocks p
                    WHERE p.bch_name = NEW.bch_name
                      AND p.block_number = NEW.prev_line_number
                      AND p.this_line_number IS NOT NULL
                      AND (
                            (NEW.msg_type = 1 AND
                                (NEW.this_line_number = p.this_line_number OR NEW.this_line_number = p.this_line_number + 1)
                            )
                            OR
                            (NEW.msg_type IN (0,3,4) AND NEW.this_line_number = p.this_line_number + 1)
                          )
                    LIMIT 1
                  );
            END;
            """);
    }

    private static void createConnectionStateTrigger(Statement st) throws SQLException {
        int FRIEND     = (int) DatabaseInitializer.CONNECTION_FRIEND;
        int CONTACT    = (int) DatabaseInitializer.CONNECTION_CONTACT;
        int FOLLOW     = (int) DatabaseInitializer.CONNECTION_FOLLOW;

        int UNFRIEND   = (int) DatabaseInitializer.CONNECTION_UNFRIEND;
        int UNCONTACT  = (int) DatabaseInitializer.CONNECTION_UNCONTACT;
        int UNFOLLOW   = (int) DatabaseInitializer.CONNECTION_UNFOLLOW;

        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_connection_state_ai
            AFTER INSERT ON blocks
            WHEN NEW.msg_type = 3
            BEGIN
                -- FRIEND/CONTACT/FOLLOW:
                -- 1) РµСЃР»Рё Р·Р°РїРёСЃРё РЅРµС‚ вЂ” СЃРѕР·РґР°С‘Рј
                INSERT OR IGNORE INTO connections_state (
                    login, rel_type, to_login, to_bch_name, to_block_number, to_block_hash
                )
                SELECT
                    NEW.login,
                    NEW.msg_sub_type,
                    COALESCE(
                        NEW.to_login,
                        CASE
                            WHEN NEW.to_bch_name IS NOT NULL
                             AND length(NEW.to_bch_name) > 4
                             AND substr(NEW.to_bch_name, length(NEW.to_bch_name) - 3, 1) = '-'
                            THEN substr(NEW.to_bch_name, 1, length(NEW.to_bch_name) - 4)
                            ELSE NULL
                        END
                    ),
                    NEW.to_bch_name,
                    NEW.to_block_number,
                    NEW.to_block_hash
                WHERE NEW.msg_sub_type IN (%d, %d, %d)
                  AND COALESCE(
                      NEW.to_login,
                      CASE
                          WHEN NEW.to_bch_name IS NOT NULL
                           AND length(NEW.to_bch_name) > 4
                           AND substr(NEW.to_bch_name, length(NEW.to_bch_name) - 3, 1) = '-'
                          THEN substr(NEW.to_bch_name, 1, length(NEW.to_bch_name) - 4)
                          ELSE NULL
                      END
                  ) IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL;

                -- 2) РµСЃР»Рё Р·Р°РїРёСЃСЊ РµСЃС‚СЊ вЂ” РѕР±РЅРѕРІР»СЏРµРј Р°РєС‚СѓР°Р»СЊРЅС‹Рµ to_*
                UPDATE connections_state
                SET
                    to_bch_name     = NEW.to_bch_name,
                    to_block_number = NEW.to_block_number,
                    to_block_hash   = NEW.to_block_hash
                WHERE login = NEW.login
                  AND rel_type = NEW.msg_sub_type
                  AND to_login = COALESCE(
                      NEW.to_login,
                      CASE
                          WHEN NEW.to_bch_name IS NOT NULL
                           AND length(NEW.to_bch_name) > 4
                           AND substr(NEW.to_bch_name, length(NEW.to_bch_name) - 3, 1) = '-'
                          THEN substr(NEW.to_bch_name, 1, length(NEW.to_bch_name) - 4)
                          ELSE NULL
                      END
                  )
                  AND NEW.msg_sub_type IN (%d, %d)
                  AND COALESCE(
                      NEW.to_login,
                      CASE
                          WHEN NEW.to_bch_name IS NOT NULL
                           AND length(NEW.to_bch_name) > 4
                           AND substr(NEW.to_bch_name, length(NEW.to_bch_name) - 3, 1) = '-'
                          THEN substr(NEW.to_bch_name, 1, length(NEW.to_bch_name) - 4)
                          ELSE NULL
                      END
                  ) IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL;

                -- UNFRIEND/UNCONTACT/UNFOLLOW:
                -- СѓРґР°Р»СЏРµРј СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓСЋС‰РµРµ "РїРѕР·РёС‚РёРІРЅРѕРµ" СЃРѕСЃС‚РѕСЏРЅРёРµ
                DELETE FROM connections_state
                WHERE login = NEW.login
                  AND to_login = COALESCE(
                      NEW.to_login,
                      CASE
                          WHEN NEW.to_bch_name IS NOT NULL
                           AND length(NEW.to_bch_name) > 4
                           AND substr(NEW.to_bch_name, length(NEW.to_bch_name) - 3, 1) = '-'
                          THEN substr(NEW.to_bch_name, 1, length(NEW.to_bch_name) - 4)
                          ELSE NULL
                      END
                  )
                  AND rel_type = CASE NEW.msg_sub_type
                      WHEN %d THEN %d
                      WHEN %d THEN %d
                      WHEN %d THEN %d
                      ELSE rel_type
                  END
                  AND COALESCE(
                      NEW.to_login,
                      CASE
                          WHEN NEW.to_bch_name IS NOT NULL
                           AND length(NEW.to_bch_name) > 4
                           AND substr(NEW.to_bch_name, length(NEW.to_bch_name) - 3, 1) = '-'
                          THEN substr(NEW.to_bch_name, 1, length(NEW.to_bch_name) - 4)
                          ELSE NULL
                      END
                  ) IS NOT NULL
                  AND NEW.msg_sub_type IN (%d, %d, %d);
            END;
            """.formatted(
                FRIEND, CONTACT, FOLLOW,
                FRIEND, CONTACT,

                UNFRIEND,  FRIEND,
                UNCONTACT, CONTACT,
                UNFOLLOW,  FOLLOW,

                UNFRIEND, UNCONTACT, UNFOLLOW
            ));
    }

    private static void createMessageStatsLikeTrigger(Statement st) throws SQLException {
        int LIKE = (int) DatabaseInitializer.REACTION_LIKE;
        int UNLIKE = (int) DatabaseInitializer.REACTION_UNLIKE;

        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_message_stats_like_ai
            AFTER INSERT ON blocks
            WHEN NEW.msg_type = 2 AND NEW.msg_sub_type IN (%d, %d)
            BEGIN
                -- ensure target stats row exists
                INSERT OR IGNORE INTO message_stats (
                    to_login, to_bch_name, to_block_number, to_block_hash,
                    likes_count, replies_count, edits_count
                )
                SELECT
                    NEW.to_login, NEW.to_bch_name, NEW.to_block_number, NEW.to_block_hash,
                    0, 0, 0
                WHERE NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;

                -- apply delta by state transition (none/unlike->like = +1, like->unlike = -1)
                UPDATE message_stats
                SET likes_count = MAX(
                    0,
                    likes_count + (
                        CASE
                            WHEN NEW.msg_sub_type = %d
                                 AND COALESCE((
                                     SELECT b.msg_sub_type
                                     FROM blocks b
                                     WHERE b.login = NEW.login
                                       AND b.bch_name = NEW.bch_name
                                       AND b.msg_type = 2
                                       AND b.to_login = NEW.to_login
                                       AND b.to_bch_name = NEW.to_bch_name
                                       AND b.to_block_number = NEW.to_block_number
                                       AND b.to_block_hash = NEW.to_block_hash
                                       AND b.block_number < NEW.block_number
                                     ORDER BY b.block_number DESC
                                     LIMIT 1
                                 ), -1) <> %d
                            THEN 1
                            WHEN NEW.msg_sub_type = %d
                                 AND COALESCE((
                                     SELECT b.msg_sub_type
                                     FROM blocks b
                                     WHERE b.login = NEW.login
                                       AND b.bch_name = NEW.bch_name
                                       AND b.msg_type = 2
                                       AND b.to_login = NEW.to_login
                                       AND b.to_bch_name = NEW.to_bch_name
                                       AND b.to_block_number = NEW.to_block_number
                                       AND b.to_block_hash = NEW.to_block_hash
                                       AND b.block_number < NEW.block_number
                                     ORDER BY b.block_number DESC
                                     LIMIT 1
                                 ), -1) = %d
                            THEN -1
                            ELSE 0
                        END
                    )
                )
                WHERE to_login = NEW.to_login
                  AND to_bch_name = NEW.to_bch_name
                  AND to_block_number = NEW.to_block_number
                  AND to_block_hash = NEW.to_block_hash
                  AND NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;

                -- persist latest actor->target reaction state
                INSERT OR IGNORE INTO reactions_state (
                    from_login, from_bch_name, reaction_type,
                    to_login, to_bch_name, to_block_number, to_block_hash,
                    last_sub_type
                )
                SELECT
                    NEW.login, NEW.bch_name, %d,
                    NEW.to_login, NEW.to_bch_name, NEW.to_block_number, NEW.to_block_hash,
                    NEW.msg_sub_type
                WHERE NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;

                UPDATE reactions_state
                SET last_sub_type = NEW.msg_sub_type
                WHERE from_login = NEW.login
                  AND from_bch_name = NEW.bch_name
                  AND reaction_type = %d
                  AND to_login = NEW.to_login
                  AND to_bch_name = NEW.to_bch_name
                  AND to_block_number = NEW.to_block_number
                  AND to_block_hash = NEW.to_block_hash
                  AND NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;
            END;
            """.formatted(
                LIKE, UNLIKE,
                LIKE, LIKE,
                UNLIKE, LIKE,
                LIKE,
                LIKE
            ));
    }

    private static void createMessageStatsReplyTrigger(Statement st) throws SQLException {
        int REPLY = (int) DatabaseInitializer.TEXT_REPLY;

        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_message_stats_reply_ai
            AFTER INSERT ON blocks
            WHEN NEW.msg_type = 1 AND NEW.msg_sub_type = %d
            BEGIN
                INSERT OR IGNORE INTO message_stats (
                    to_login, to_bch_name, to_block_number, to_block_hash,
                    likes_count, replies_count, edits_count
                )
                SELECT
                    NEW.to_login, NEW.to_bch_name, NEW.to_block_number, NEW.to_block_hash,
                    0, 0, 0
                WHERE NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;

                UPDATE message_stats
                SET replies_count = replies_count + 1
                WHERE to_login = NEW.to_login
                  AND to_bch_name = NEW.to_bch_name
                  AND to_block_number = NEW.to_block_number
                  AND to_block_hash = NEW.to_block_hash
                  AND NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;
            END;
            """.formatted(REPLY));
    }

    private static void createEditApplyTrigger(Statement st) throws SQLException {
        int EDIT_POST = (int) DatabaseInitializer.TEXT_EDIT_POST;
        int EDIT_REPLY = (int) DatabaseInitializer.TEXT_EDIT_REPLY;

        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_edit_apply_ai
            AFTER INSERT ON blocks
            WHEN NEW.msg_type = 1 AND NEW.msg_sub_type IN (%d, %d)
            BEGIN
                -- 1) РїРѕРјРµС‡Р°РµРј РёСЃС…РѕРґРЅС‹Р№ Р±Р»РѕРє, С‡С‚Рѕ РµРіРѕ "РїРµСЂРµРєСЂС‹Р»" СЌС‚РѕС‚ edit
                UPDATE blocks
                SET edited_by_block_number = NEW.block_number
                WHERE login = NEW.login
                  AND bch_name = NEW.bch_name
                  AND block_number = NEW.to_block_number
                  AND NEW.to_block_number IS NOT NULL;

                -- 2) СЃРѕР·РґР°С‘Рј stats-СЃС‚СЂРѕРєСѓ РµСЃР»Рё РµС‘ РЅРµ Р±С‹Р»Рѕ
                INSERT OR IGNORE INTO message_stats (
                    to_login, to_bch_name, to_block_number, to_block_hash,
                    likes_count, replies_count, edits_count
                )
                SELECT
                    NEW.to_login, NEW.to_bch_name, NEW.to_block_number, NEW.to_block_hash,
                    0, 0, 0
                WHERE NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;

                -- 3) +1 edit
                UPDATE message_stats
                SET edits_count = edits_count + 1
                WHERE to_login = NEW.to_login
                  AND to_bch_name = NEW.to_bch_name
                  AND to_block_number = NEW.to_block_number
                  AND to_block_hash = NEW.to_block_hash
                  AND NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;
            END;
            """.formatted(EDIT_POST, EDIT_REPLY));
    }
}

