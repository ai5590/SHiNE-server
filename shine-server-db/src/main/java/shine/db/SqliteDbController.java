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
    private final Connection connection;

    private SqliteDbController() {
        try {
            // Подгружаем драйвер SQLite
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        String dbPath = AppConfig.getInstance().getParam("db.path");

        if (dbPath == null || dbPath.isBlank()) {
            throw new RuntimeException("Config param 'db.path' is not set in application.properties");
        }

        Path dbFile = Paths.get(dbPath);

        // 👉 Если файла БД нет — создаём новую БД через DatabaseInitializer
        if (!Files.exists(dbFile)) {
            System.out.println("[DB] Файл БД не найден: " + dbFile.toAbsolutePath());
            System.out.println("[DB] Создаём новую БД с помощью DatabaseInitializer...");

            // можно передать пустой массив аргументов
            DatabaseInitializer.createNewDB(new String[0]);
        }

        String url = "jdbc:sqlite:" + dbPath;

        try {
            this.connection = DriverManager.getConnection(url);
            this.connection.setAutoCommit(true);

            // ВАЖНО: включаем поддержку внешних ключей для этого соединения
            try (Statement st = this.connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to SQLite database: " + url, e);
        }
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

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            // логировать по необходимости
        }
    }
}
