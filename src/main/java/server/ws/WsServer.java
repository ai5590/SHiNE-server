package server.ws;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.config.AppConfig;

import java.time.Duration;

/**
 * WsServer — поднимает Jetty WS на /ws (порт 8080).
 */
public final class WsServer {
    private static final Logger log = LoggerFactory.getLogger(WsServer.class);

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.getInstance();
        int port = 7070;
        try {
            String portStr = config.getParam("server.port");
            if (portStr != null && !portStr.isBlank()) {
                port = Integer.parseInt(portStr.trim());
            }
        } catch (Exception e) {
            log.info("Не удалось прочитать параметр server.port, используем порт по умолчанию {}", port);
        }


        Server server = new Server(port);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        // Инициализация контейнера WebSocket
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            // Таймаут простоя соединения (Jetty 11 синтаксис)
            wsContainer.setIdleTimeout(Duration.ofMinutes(5));

            // Маппинг эндпоинта
            wsContainer.addMapping("/ws", (req, resp) -> new BlockchainWsEndpoint());
        });

        server.start();
        log.info("✅ WS сервер запущен на ws://localhost:{}/ws", port);
        server.join();
    }
}
