package server.logic.ws_protocol.JSON.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class FcmPushSender {
    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private FcmPushSender() {}

    public static boolean sendNotification(String token, String title, String body, String messageId) {
        try {
            String serverKey = AppConfig.getInstance().getStringOrEmpty("fcm.server.key");
            if (serverKey.isBlank()) {
                log.warn("fcm.server.key is empty, skip FCM send");
                return false;
            }

            ObjectNode root = MAPPER.createObjectNode();
            root.put("to", token);
            ObjectNode notif = root.putObject("notification");
            notif.put("title", title);
            notif.put("body", body);
            ObjectNode data = root.putObject("data");
            data.put("messageId", messageId);

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/fcm/send"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "key=" + serverKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            log.warn("FCM send failed", e);
            return false;
        }
    }
}
