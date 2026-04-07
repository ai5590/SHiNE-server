package utils.config;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {

    private static volatile AppConfig instance;
    private final Properties properties = new Properties();

    private AppConfig() {
        load();
    }

    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }

    private void load() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {

            if (in == null) {
                throw new RuntimeException("Config file application.properties not found");
            }

            properties.load(in);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    /** Вернёт значение строки или null, если параметр не найден */
    public String getParam(String name) {
        String fromSystem = System.getProperty(name);
        if (fromSystem != null) return fromSystem;
        return properties.getProperty(name);
    }

    /** Вернёт строку или пустую строку, если параметр не найден. */
    public String getStringOrEmpty(String name) {
        String value = properties.getProperty(name);
        return value == null ? "" : value.trim();
    }

    /** Можно добавить методы для удобства */
    public int getInt(String name, int defaultValue) {
        String v = properties.getProperty(name);
        return v == null ? defaultValue : Integer.parseInt(v);
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        String v = properties.getProperty(name);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }
}
