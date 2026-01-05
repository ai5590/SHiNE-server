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
 * Таблицы:
 *  - solana_users
 *  - active_sessions
 *  - users_params
 *  - ip_geo_cache
 *  - blockchain_state
 *  - blocks
 *  - connections_state
 *  - message_stats
 */
public class DatabaseInitializer {

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
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS solana_users (
                    login       TEXT    NOT NULL PRIMARY KEY,
                    device_key  TEXT    NOT NULL,
                    solana_key  TEXT
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_solana_users_login
                ON solana_users (login);
                """);

            // 2. active_sessions
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS active_sessions (
                    session_id                 TEXT    NOT NULL PRIMARY KEY,
                    login                      TEXT    NOT NULL,
                    session_pwd                TEXT    NOT NULL,
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
                    blockchain_name       TEXT    NOT NULL PRIMARY KEY,
                    login                 TEXT    NOT NULL,
                    blockchain_key        TEXT    NOT NULL,
                
                    size_limit            INTEGER NOT NULL,
                    file_size_bytes       INTEGER NOT NULL,
                
                    last_global_number    INTEGER NOT NULL,
                    last_global_hash      TEXT    NOT NULL,
                    updated_at_ms         INTEGER NOT NULL,
                
                    line0_last_number     INTEGER NOT NULL,
                    line0_last_hash       TEXT    NOT NULL,
                    line1_last_number     INTEGER NOT NULL,
                    line1_last_hash       TEXT    NOT NULL,
                    line2_last_number     INTEGER NOT NULL,
                    line2_last_hash       TEXT    NOT NULL,
                    line3_last_number     INTEGER NOT NULL,
                    line3_last_hash       TEXT    NOT NULL,
                    line4_last_number     INTEGER NOT NULL,
                    line4_last_hash       TEXT    NOT NULL,
                    line5_last_number     INTEGER NOT NULL,
                    line5_last_hash       TEXT    NOT NULL,
                    line6_last_number     INTEGER NOT NULL,
                    line6_last_hash       TEXT    NOT NULL,
                    line7_last_number     INTEGER NOT NULL,
                    line7_last_hash       TEXT    NOT NULL,
                    
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

            // 6. blocks
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS blocks (
                    login                     TEXT    NOT NULL,
                    bch_name                  TEXT    NOT NULL,
                    block_global_number       INTEGER NOT NULL,
                    block_global_pre_hashe    TEXT    NOT NULL,

                    block_line_index          INTEGER NOT NULL,
                    block_line_number         INTEGER NOT NULL,
                    block_line_pre_hashe      TEXT    NOT NULL,

                    msg_type                  INTEGER NOT NULL,
                    msg_sub_type              INTEGER NOT NULL,

                    block_byte                BLOB,

                    to_login                  TEXT,
                    to_bch_name               TEXT,
                    to_block_global_number    INTEGER,
                    to_block_hashe            TEXT,

                    FOREIGN KEY (login) REFERENCES solana_users(login),
                    FOREIGN KEY (bch_name) REFERENCES blockchain_state(blockchain_name)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_chain_global
                ON blocks (login, bch_name, block_global_number);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_to_target
                ON blocks (to_login, to_bch_name, to_block_global_number);
                """);

            // 7) connections_state
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS connections_state (
                    login                  TEXT    NOT NULL,
                    rel_type               INTEGER NOT NULL,
                    to_login               TEXT    NOT NULL,
                    to_bch_name            TEXT    NOT NULL,
                    to_block_global_number INTEGER,
                    to_block_hashe         TEXT,

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

            // 8) Trigger: connection state
            st.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS trg_blocks_connection_state_ai
                AFTER INSERT ON blocks
                WHEN NEW.msg_type = 3
                BEGIN

                    INSERT INTO connections_state (
                        login, rel_type, to_login, to_bch_name, to_block_global_number, to_block_hashe
                    )
                    SELECT
                        NEW.login,
                        NEW.msg_sub_type,
                        NEW.to_login,
                        NEW.to_bch_name,
                        NEW.to_block_global_number,
                        NEW.to_block_hashe
                    WHERE NEW.msg_sub_type IN (10, 20, 30)
                      AND NEW.to_login IS NOT NULL
                      AND NEW.to_bch_name IS NOT NULL
                    ON CONFLICT(login, rel_type, to_login)
                    DO UPDATE SET
                        to_bch_name = excluded.to_bch_name,
                        to_block_global_number = excluded.to_block_global_number,
                        to_block_hashe = excluded.to_block_hashe;

                    DELETE FROM connections_state
                    WHERE login = NEW.login
                      AND to_login = NEW.to_login
                      AND rel_type = CASE NEW.msg_sub_type
                          WHEN 11 THEN 10
                          WHEN 21 THEN 20
                          WHEN 31 THEN 30
                          ELSE rel_type
                      END
                      AND NEW.msg_sub_type IN (11, 21, 31);

                END;
                """);

            // 9) message_stats
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS message_stats (
                    to_login               TEXT    NOT NULL,
                    to_bch_name            TEXT    NOT NULL,
                    to_block_global_number INTEGER NOT NULL,
                    to_block_hash          TEXT    NOT NULL,

                    likes_count            INTEGER NOT NULL DEFAULT 0,
                    replies_count          INTEGER NOT NULL DEFAULT 0,

                    UNIQUE (
                        to_login,
                        to_bch_name,
                        to_block_global_number,
                        to_block_hash
                    )
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_message_stats_target
                ON message_stats (to_bch_name, to_block_global_number, to_block_hash);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_message_stats_login
                ON message_stats (to_login);
                """);

            // 10) Trigger: LIKE
            st.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS trg_blocks_message_stats_like_ai
                AFTER INSERT ON blocks
                WHEN NEW.msg_type = 2 AND NEW.msg_sub_type = 1
                BEGIN
                    INSERT INTO message_stats (
                        to_login,
                        to_bch_name,
                        to_block_global_number,
                        to_block_hash,
                        likes_count,
                        replies_count
                    )
                    SELECT
                        substr(NEW.to_bch_name, 1, length(NEW.to_bch_name) - 3),
                        NEW.to_bch_name,
                        NEW.to_block_global_number,
                        NEW.to_block_hashe,
                        1,
                        0
                    WHERE NEW.to_bch_name IS NOT NULL
                      AND length(NEW.to_bch_name) > 3
                      AND NEW.to_block_global_number IS NOT NULL
                      AND NEW.to_block_hashe IS NOT NULL
                    ON CONFLICT(to_login, to_bch_name, to_block_global_number, to_block_hash)
                    DO UPDATE SET
                        likes_count = message_stats.likes_count + 1;
                END;
                """);

            // 11) Trigger: REPLY
            st.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS trg_blocks_message_stats_reply_ai
                AFTER INSERT ON blocks
                WHEN NEW.msg_type = 1 AND NEW.msg_sub_type = 2
                BEGIN
                    INSERT INTO message_stats (
                        to_login,
                        to_bch_name,
                        to_block_global_number,
                        to_block_hash,
                        likes_count,
                        replies_count
                    )
                    SELECT
                        substr(NEW.to_bch_name, 1, length(NEW.to_bch_name) - 3),
                        NEW.to_bch_name,
                        NEW.to_block_global_number,
                        NEW.to_block_hashe,
                        0,
                        1
                    WHERE NEW.to_bch_name IS NOT NULL
                      AND length(NEW.to_bch_name) > 3
                      AND NEW.to_block_global_number IS NOT NULL
                      AND NEW.to_block_hashe IS NOT NULL
                    ON CONFLICT(to_login, to_bch_name, to_block_global_number, to_block_hash)
                    DO UPDATE SET
                        replies_count = message_stats.replies_count + 1;
                END;
                """);
        }
    }
}