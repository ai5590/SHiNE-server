package shine.db;

import utils.config.AppConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseInitializer — создание новой SQLite-БД по схеме SHiNE.
 *
 * В этой версии:
 *  - создаём ТОЛЬКО таблицы/индексы
 *  - в конце вызываем DatabaseTriggersInstaller.createAllTriggers(st)
 *
 * v2 (sessions):
 *  - active_sessions.session_pwd удалён
 *  - active_sessions.session_key хранит публичный ключ сессии (sessionPubKeyB64)
 */
public final class DatabaseInitializer {

    private DatabaseInitializer() {}

    /* ===================== TEXT (msg_type=1) ===================== */

    public static final short TEXT_NEW = 1;
    public static final short TEXT_REPLY = 2;
    public static final short TEXT_REPOST = 3;
    public static final short TEXT_EDIT = 10;

    /* ===================== REACTION (msg_type=2) ===================== */

    public static final short REACTION_LIKE = 1;

    /* ===================== CONNECTION (msg_type=3) ===================== */
    public static final short CONNECTION_FRIEND     = 10;
    public static final short CONNECTION_UNFRIEND   = 11;

    public static final short CONNECTION_CONTACT    = 20;
    public static final short CONNECTION_UNCONTACT  = 21;

    public static final short CONNECTION_FOLLOW     = 30;
    public static final short CONNECTION_UNFOLLOW   = 31;

    public static void createNewDB(String[] args) {
        AppConfig config = AppConfig.getInstance();
        String dbPath = config.getParam("db.path");

        if (dbPath == null || dbPath.isBlank()) {
            System.err.println("Параметр db.path не задан в application.properties");
            return;
        }

        Path dbFile = Paths.get(dbPath);
        try {
            Path parent = dbFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (Files.exists(dbFile)) {
                System.out.println("Файл базы данных уже существует: " + dbFile.toAbsolutePath());
                System.out.print("Пересоздать БД (СТАРАЯ БУДЕТ УДАЛЕНА)? [y/N]: ");

                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String answer = reader.readLine();
                if (!"y".equalsIgnoreCase(answer) && !"yes".equalsIgnoreCase(answer)) {
                    System.out.println("Операция отменена. БД не изменена.");
                    return;
                }

                Files.delete(dbFile);
                System.out.println("Старый файл БД удалён.");
            }

            createSchema("jdbc:sqlite:" + dbPath);
            System.out.println("Новая БД успешно создана по пути: " + dbFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Ошибка работы с файлом БД: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Ошибка создания схемы БД: " + e.getMessage());
        }
    }

    private static void createSchema(String jdbcUrl) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {

            st.execute("PRAGMA foreign_keys = ON");

            // 1. solana_users
            // ВАЖНО:
            // - Все требуемые поля теперь лежат в solana_users:
            //   login, blockchain_name, solana_key, blockchain_key, device_key
            // - Поиск по login в DAO сделан case-insensitive.
            // - Для защиты от дублей "Anya" и "anya" добавляем COLLATE NOCASE на PRIMARY KEY.
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS solana_users (
                    login           TEXT    NOT NULL PRIMARY KEY COLLATE NOCASE,
                    blockchain_name TEXT    NOT NULL,
                    solana_key      TEXT    NOT NULL,
                    blockchain_key  TEXT    NOT NULL,
                    device_key      TEXT    NOT NULL
                );
                """);

            st.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_solana_users_blockchain_name
                ON solana_users (blockchain_name);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_solana_users_login
                ON solana_users (login);
                """);

            // 2. active_sessions (v2)
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS active_sessions (
                    session_id                 TEXT    NOT NULL PRIMARY KEY,
                    login                      TEXT    NOT NULL,
                    session_key                TEXT    NOT NULL,
                    storage_pwd                TEXT    NOT NULL,
                    session_created_at_ms      INTEGER NOT NULL,
                    last_authirificated_at_ms  INTEGER NOT NULL,
                    push_endpoint              TEXT,
                    push_p256dh_key            TEXT,
                    push_auth_key              TEXT,
                    client_ip                  TEXT,
                    client_info_from_client    TEXT,
                    client_info_from_request   TEXT,
                    user_language              TEXT,
                    FOREIGN KEY (login) REFERENCES solana_users(login)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_active_sessions_login
                ON active_sessions (login);
                """);

            // 3. users_params
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users_params (
                    login          TEXT    NOT NULL,
                    param          TEXT    NOT NULL,
                    time_ms        INTEGER NOT NULL,
                    value          TEXT    NOT NULL,
                    device_key     TEXT,
                    signature      TEXT,
                    FOREIGN KEY (login) REFERENCES solana_users(login),
                    UNIQUE (login, param)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_users_params_login
                ON users_params (login);
                """);

            // 4. ip_geo_cache
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ip_geo_cache (
                    ip             TEXT    NOT NULL PRIMARY KEY,
                    geo            TEXT,
                    updated_at_ms  INTEGER NOT NULL
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_ip_geo_cache_updated_at
                ON ip_geo_cache (updated_at_ms);
                """);

            // 5. blockchain_state
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS blockchain_state (
                    blockchain_name   TEXT    NOT NULL PRIMARY KEY,
                    login             TEXT    NOT NULL,
                    blockchain_key    TEXT    NOT NULL,

                    size_limit        INTEGER NOT NULL,
                    file_size_bytes   INTEGER NOT NULL,

                    last_block_number INTEGER NOT NULL,
                    last_block_hash   BLOB,

                    updated_at_ms     INTEGER NOT NULL,

                    FOREIGN KEY (login) REFERENCES solana_users(login)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blockchain_state_login
                ON blockchain_state (login);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blockchain_state_updated_at
                ON blockchain_state (updated_at_ms);
                """);

            // 6. blocks (+ line_code)
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS blocks (
                    login                TEXT    NOT NULL,
                    bch_name             TEXT    NOT NULL,
                    block_number         INTEGER NOT NULL CHECK(block_number >= 0),

                    msg_type             INTEGER NOT NULL,
                    msg_sub_type         INTEGER NOT NULL,

                    block_bytes          BLOB    NOT NULL,

                    -- target (reply/like/edit и т.д.)
                    to_login             TEXT,
                    to_bch_name          TEXT,
                    to_block_number      INTEGER CHECK(to_block_number IS NULL OR to_block_number >= 0),
                    to_block_hash        BLOB,

                    -- собственные данные
                    block_hash           BLOB    NOT NULL,
                    block_signature      BLOB    NOT NULL,

                    -- если этот блок был изменён последним edit'ом
                    edited_by_block_number INTEGER CHECK(edited_by_block_number IS NULL OR edited_by_block_number >= 0),

                    -- линейность (опционально)
                    line_code           INTEGER CHECK(line_code IS NULL OR line_code >= 0),
                    prev_line_number    INTEGER CHECK(prev_line_number IS NULL OR prev_line_number >= 0),
                    prev_line_hash      BLOB,
                    this_line_number    INTEGER CHECK(this_line_number IS NULL OR this_line_number >= 0),

                    FOREIGN KEY (login) REFERENCES solana_users(login),
                    FOREIGN KEY (bch_name) REFERENCES blockchain_state(blockchain_name),

                    UNIQUE (bch_name, block_number)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_by_chain_number
                ON blocks (bch_name, block_number);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_to_target
                ON blocks (to_login, to_bch_name, to_block_number);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_by_line
                ON blocks (bch_name, line_code, this_line_number);
                """);

            // 7) connections_state
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS connections_state (
                    login           TEXT    NOT NULL,
                    rel_type        INTEGER NOT NULL,
                    to_login        TEXT    NOT NULL,
                    to_bch_name     TEXT    NOT NULL,
                    to_block_number INTEGER,
                    to_block_hash   BLOB,

                    FOREIGN KEY (login) REFERENCES solana_users(login),

                    UNIQUE (login, rel_type, to_login)
                );
                """);

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

            // 8) message_stats
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS message_stats (
                    to_login          TEXT    NOT NULL,
                    to_bch_name       TEXT    NOT NULL,
                    to_block_number   INTEGER NOT NULL,
                    to_block_hash     BLOB    NOT NULL,

                    likes_count       INTEGER NOT NULL DEFAULT 0,
                    replies_count     INTEGER NOT NULL DEFAULT 0,
                    edits_count       INTEGER NOT NULL DEFAULT 0,

                    UNIQUE (
                        to_login,
                        to_bch_name,
                        to_block_number,
                        to_block_hash
                    )
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_message_stats_target
                ON message_stats (to_bch_name, to_block_number, to_block_hash);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_message_stats_login
                ON message_stats (to_login);
                """);

            DatabaseTriggersInstaller.createAllTriggers(st);
        }
    }
}