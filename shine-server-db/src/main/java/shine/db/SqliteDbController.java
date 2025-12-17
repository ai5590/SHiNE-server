package shine.db;

import utils.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
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

    /**
     * Каждый вызов возвращает НОВОЕ соединение.
     * Закрывать обязан вызывающий код (try-with-resources).
     */
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

    /** Теперь close() не нужен. */
    public void close() {
        // no-op
    }
}