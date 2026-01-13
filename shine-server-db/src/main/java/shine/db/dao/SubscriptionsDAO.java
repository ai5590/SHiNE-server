package shine.db.dao;

import shine.db.MsgSubType;
import shine.db.SqliteDbController;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SubscriptionsDAO — агрегатный DAO для "каналов" (подписок).
 *
 * Возвращает по каждой активной подписке (FOLLOW) + "сам на себя":
 *  - login цели (channelLogin)
 *  - blockchainName цели (channelBchName)
 *  - count публикаций (TEXT_NEW)
 *  - last publication: bytes оригинального блока (для timestamp)
 *  - last publication: bytes актуального блока (edit или orig) — для текста превью
 *
 * Важно:
 * - это НЕ таблица => сущность результата хранится вложенным классом.
 * - методы с Connection НЕ закрывают соединение
 * - методы без Connection сами открывают и закрывают соединение
 */
public final class SubscriptionsDAO {

    private static volatile SubscriptionsDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private SubscriptionsDAO() {}

    public static SubscriptionsDAO getInstance() {
        if (instance == null) {
            synchronized (SubscriptionsDAO.class) {
                if (instance == null) instance = new SubscriptionsDAO();
            }
        }
        return instance;
    }

    /** Результат одной строки ("канал") для подписок. */
    public static final class ChannelRow {

        private final String channelLogin;
        private final String channelBchName;

        private final int publicationsCount;

        /** Последняя публикация: global number (nullable если публикаций нет). */
        private final Integer lastPublicationGlobalNumber;

        /** Байты оригинальной публикации (FULL bytes блока) — для timestamp (nullable). */
        private final byte[] lastPublicationBlockBytes;

        /** Если публикация редактировалась: global number edit-блока (nullable). */
        private final Integer lastEditGlobalNumber;

        /** Байты edit-блока (FULL bytes блока) (nullable). */
        private final byte[] lastEditBlockBytes;

        public ChannelRow(String channelLogin,
                          String channelBchName,
                          int publicationsCount,
                          Integer lastPublicationGlobalNumber,
                          byte[] lastPublicationBlockBytes,
                          Integer lastEditGlobalNumber,
                          byte[] lastEditBlockBytes) {

            this.channelLogin = channelLogin;
            this.channelBchName = channelBchName;
            this.publicationsCount = publicationsCount;
            this.lastPublicationGlobalNumber = lastPublicationGlobalNumber;
            this.lastPublicationBlockBytes = lastPublicationBlockBytes;
            this.lastEditGlobalNumber = lastEditGlobalNumber;
            this.lastEditBlockBytes = lastEditBlockBytes;
        }

        public String getChannelLogin() { return channelLogin; }
        public String getChannelBchName() { return channelBchName; }

        public int getPublicationsCount() { return publicationsCount; }

        public Integer getLastPublicationGlobalNumber() { return lastPublicationGlobalNumber; }
        public byte[] getLastPublicationBlockBytes() { return lastPublicationBlockBytes; }

        public Integer getLastEditGlobalNumber() { return lastEditGlobalNumber; }
        public byte[] getLastEditBlockBytes() { return lastEditBlockBytes; }
    }

    // В проекте msg_type=1 означает TEXT (у тебя это уже зафиксировано).
    private static final int MSG_TYPE_TEXT = 1;

    /**
     * Получить список подписок (активные FOLLOW) + "сам на себя" и по каждой:
     * - count публикаций (TEXT_NEW)
     * - последнюю публикацию (orig bytes) + её edit (если есть)
     *
     * Поведение при 0 публикаций:
     * - publications_count = 0
     * - last_pub_* = NULL
     * - last_edit_* = NULL
     */
    public List<ChannelRow> getSubscribedChannels(Connection c, String requesterLogin) throws SQLException {

        String sql = """
            WITH subs AS (
                -- 1) FOLLOW-каналы
                SELECT
                    cs.to_login  AS channel_login,
                    cs.to_bch_name AS channel_bch_name
                FROM connections_state cs
                WHERE cs.login = ?
                  AND cs.rel_type = ?

                UNION

                -- 2) self: все блокчейны пользователя (если их несколько)
                SELECT
                    bs.login            AS channel_login,
                    bs.blockchain_name  AS channel_bch_name
                FROM blockchain_state bs
                WHERE bs.login = ?
            ),
            pub_counts AS (
                SELECT
                    b.login AS channel_login,
                    b.bch_name AS channel_bch_name,
                    COUNT(*) AS publications_count
                FROM blocks b
                JOIN subs s
                  ON s.channel_login = b.login
                 AND s.channel_bch_name = b.bch_name
                WHERE b.msg_type = ?
                  AND b.msg_sub_type = ?
                GROUP BY b.login, b.bch_name
            ),
            last_pub AS (
                SELECT
                    b.login AS channel_login,
                    b.bch_name AS channel_bch_name,
                    MAX(b.block_global_number) AS last_pub_global_number
                FROM blocks b
                JOIN subs s
                  ON s.channel_login = b.login
                 AND s.channel_bch_name = b.bch_name
                WHERE b.msg_type = ?
                  AND b.msg_sub_type = ?
                GROUP BY b.login, b.bch_name
            ),
            last_pub_block AS (
                SELECT
                    b.login AS channel_login,
                    b.bch_name AS channel_bch_name,
                    b.block_global_number AS last_pub_global_number,
                    b.block_byte AS last_pub_block_bytes,
                    b.edited_by_block_global_number AS last_edit_global_number
                FROM blocks b
                JOIN last_pub lp
                  ON lp.channel_login = b.login
                 AND lp.channel_bch_name = b.bch_name
                 AND lp.last_pub_global_number = b.block_global_number
            ),
            last_edit_block AS (
                SELECT
                    e.login AS channel_login,
                    e.bch_name AS channel_bch_name,
                    e.block_global_number AS last_edit_global_number,
                    e.block_byte AS last_edit_block_bytes
                FROM blocks e
                JOIN last_pub_block p
                  ON p.channel_login = e.login
                 AND p.channel_bch_name = e.bch_name
                 AND p.last_edit_global_number = e.block_global_number
            )
            SELECT
                s.channel_login,
                s.channel_bch_name,
                COALESCE(pc.publications_count, 0) AS publications_count,
                p.last_pub_global_number,
                p.last_pub_block_bytes,
                p.last_edit_global_number,
                e.last_edit_block_bytes
            FROM subs s
            LEFT JOIN pub_counts pc
              ON pc.channel_login = s.channel_login
             AND pc.channel_bch_name = s.channel_bch_name
            LEFT JOIN last_pub_block p
              ON p.channel_login = s.channel_login
             AND p.channel_bch_name = s.channel_bch_name
            LEFT JOIN last_edit_block e
              ON e.channel_login = s.channel_login
             AND e.channel_bch_name = s.channel_bch_name
            ORDER BY s.channel_login, s.channel_bch_name
            """;

        List<ChannelRow> out = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;

            // FOLLOW
            ps.setString(i++, requesterLogin);
            ps.setInt(i++, (int) MsgSubType.CONNECTION_FOLLOW);

            // self
            ps.setString(i++, requesterLogin);

            // pub_counts
            ps.setInt(i++, MSG_TYPE_TEXT);
            ps.setInt(i++, (int) MsgSubType.TEXT_NEW);

            // last_pub
            ps.setInt(i++, MSG_TYPE_TEXT);
            ps.setInt(i++, (int) MsgSubType.TEXT_NEW);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String channelLogin = rs.getString("channel_login");
                    String channelBchName = rs.getString("channel_bch_name");

                    int publicationsCount = rs.getInt("publications_count");

                    Integer lastPubGn = (Integer) rs.getObject("last_pub_global_number");
                    byte[] lastPubBytes = rs.getBytes("last_pub_block_bytes");

                    Integer lastEditGn = (Integer) rs.getObject("last_edit_global_number");
                    byte[] lastEditBytes = rs.getBytes("last_edit_block_bytes");

                    out.add(new ChannelRow(
                            channelLogin,
                            channelBchName,
                            publicationsCount,
                            lastPubGn,
                            lastPubBytes,
                            lastEditGn,
                            lastEditBytes
                    ));
                }
            }
        }

        return out;
    }

    /** Вариант без внешнего соединения. Сам открывает/закрывает. */
    public List<ChannelRow> getSubscribedChannels(String requesterLogin) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getSubscribedChannels(c, requesterLogin);
        }
    }
}