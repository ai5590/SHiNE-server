package shine.db;

import utils.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteDbController {

    private static volatile SqliteDbController instance;

    private final String jdbcUrl;

    private SqliteDbController() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        String dbPath = AppConfig.getInstance().getParam("db.path");
        if (dbPath == null || dbPath.isBlank()) {
            throw new RuntimeException("Config param 'db.path' is not set in application.properties");
        }

        Path dbFile = Paths.get(dbPath);

        if (!Files.exists(dbFile)) {
            System.out.println("[DB] Файл БД не найден: " + dbFile.toAbsolutePath());
            System.out.println("[DB] Создаём новую БД с помощью DatabaseInitializer...");
            DatabaseInitializer.createNewDB(new String[0]);
        }

        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        ensureSchemaUpgrades();
    }

    public static SqliteDbController getInstance() {
        if (instance == null) {
            synchronized (SqliteDbController.class) {
                if (instance == null) {
                    instance = new SqliteDbController();
                }
            }
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        conn.setAutoCommit(true);

        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA synchronous = NORMAL");
            st.execute("PRAGMA busy_timeout = 5000");
        }

        return conn;
    }

    public void close() {
        // no-op
    }

    private void ensureSchemaUpgrades() {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement()) {

            c.setAutoCommit(false);
            try {
                st.execute("PRAGMA foreign_keys = OFF");

                ensureReactionsStateTable(st);

                if (!tableExists(c, "connections_state")) {
                    createConnectionsStateTable(st);
                } else if (needsConnectionsStateUpgrade(c)) {
                    rebuildConnectionsStateTable(st);
                }
                ensureChannelNamesStateTable(st);
                ensureConnectionsIndexes(st);
                ensureReactionsIndexes(st);
                ensureChannelNamesIndexes(st);

                DatabaseTriggersInstaller.createAllTriggers(st);

                st.execute("PRAGMA foreign_keys = ON");
                c.commit();
            } catch (Exception e) {
                try { c.rollback(); } catch (Exception ignored) {}
                throw new RuntimeException("DB schema upgrade failed", e);
            } finally {
                try { c.setAutoCommit(true); } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB schema upgrade failed", e);
        }
    }

    private static void ensureReactionsStateTable(Statement st) throws SQLException {
        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS reactions_state (
                from_login       TEXT    NOT NULL,
                from_bch_name    TEXT    NOT NULL,
                reaction_type    INTEGER NOT NULL,
                to_login         TEXT    NOT NULL,
                to_bch_name      TEXT    NOT NULL,
                to_block_number  INTEGER NOT NULL,
                to_block_hash    BLOB    NOT NULL,
                last_sub_type    INTEGER NOT NULL,
                UNIQUE (
                    from_login,
                    from_bch_name,
                    reaction_type,
                    to_login,
                    to_bch_name,
                    to_block_number,
                    to_block_hash
                )
            );
            """);
    }

    private static void createConnectionsStateTable(Statement st) throws SQLException {
        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS connections_state (
                login           TEXT    NOT NULL,
                rel_type        INTEGER NOT NULL,
                to_login        TEXT    NOT NULL,
                to_bch_name     TEXT    NOT NULL,
                to_block_number INTEGER NOT NULL,
                to_block_hash   BLOB    NOT NULL,
                FOREIGN KEY (login) REFERENCES solana_users(login),
                UNIQUE (login, rel_type, to_login, to_bch_name, to_block_number, to_block_hash)
            );
            """);
    }

    private static void ensureConnectionsIndexes(Statement st) throws SQLException {
        st.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_connections_state_login
            ON connections_state (login);
            """);
        st.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_connections_state_to_login
            ON connections_state (to_login);
            """);
        st.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_connections_state_pair
            ON connections_state (login, to_login);
            """);
        st.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_connections_state_target
            ON connections_state (login, rel_type, to_bch_name, to_block_number);
            """);
    }

    private static void ensureReactionsIndexes(Statement st) throws SQLException {
        st.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_reactions_state_target
            ON reactions_state (to_bch_name, to_block_number, to_block_hash);
            """);
        st.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_reactions_state_actor
            ON reactions_state (from_login, from_bch_name, reaction_type);
            """);
    }

    private static void ensureChannelNamesStateTable(Statement st) throws SQLException {
        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS channel_names_state (
                slug                      TEXT    NOT NULL PRIMARY KEY,
                display_name              TEXT    NOT NULL,
                owner_login               TEXT    NOT NULL,
                owner_bch_name            TEXT    NOT NULL,
                channel_root_block_number INTEGER NOT NULL,
                channel_root_block_hash   BLOB    NOT NULL,
                created_at_ms             INTEGER NOT NULL
            );
            """);
    }

    private static void ensureChannelNamesIndexes(Statement st) throws SQLException {
        st.executeUpdate("""
            CREATE UNIQUE INDEX IF NOT EXISTS uq_channel_names_state_slug
            ON channel_names_state (slug);
            """);
        st.executeUpdate("""
            CREATE UNIQUE INDEX IF NOT EXISTS uq_channel_names_state_target
            ON channel_names_state (owner_bch_name, channel_root_block_number, channel_root_block_hash);
            """);
        st.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_channel_names_state_owner
            ON channel_names_state (owner_login, owner_bch_name);
            """);
    }

    private static void rebuildConnectionsStateTable(Statement st) throws SQLException {
        st.executeUpdate("DROP TABLE IF EXISTS connections_state_v2");
        st.executeUpdate("""
            CREATE TABLE connections_state_v2 (
                login           TEXT    NOT NULL,
                rel_type        INTEGER NOT NULL,
                to_login        TEXT    NOT NULL,
                to_bch_name     TEXT    NOT NULL,
                to_block_number INTEGER NOT NULL,
                to_block_hash   BLOB    NOT NULL,
                FOREIGN KEY (login) REFERENCES solana_users(login),
                UNIQUE (login, rel_type, to_login, to_bch_name, to_block_number, to_block_hash)
            );
            """);

        st.executeUpdate("""
            INSERT OR IGNORE INTO connections_state_v2
                (login, rel_type, to_login, to_bch_name, to_block_number, to_block_hash)
            SELECT
                login,
                rel_type,
                to_login,
                to_bch_name,
                COALESCE(to_block_number, 0),
                COALESCE(to_block_hash, zeroblob(32))
            FROM connections_state
            WHERE login IS NOT NULL
              AND to_login IS NOT NULL
              AND to_bch_name IS NOT NULL;
            """);

        st.executeUpdate("DROP TABLE connections_state");
        st.executeUpdate("ALTER TABLE connections_state_v2 RENAME TO connections_state");
    }

    private static boolean tableExists(Connection c, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1";
        try (var ps = c.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean needsConnectionsStateUpgrade(Connection c) throws SQLException {
        boolean toBlockNumberNotNull = false;
        boolean toBlockHashNotNull = false;

        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(connections_state)")) {
            while (rs.next()) {
                String name = rs.getString("name");
                int notNull = rs.getInt("notnull");
                if ("to_block_number".equalsIgnoreCase(name)) {
                    toBlockNumberNotNull = notNull == 1;
                }
                if ("to_block_hash".equalsIgnoreCase(name)) {
                    toBlockHashNotNull = notNull == 1;
                }
            }
        }

        return !toBlockNumberNotNull || !toBlockHashNotNull;
    }
}
