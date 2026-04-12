package server.logic.ws_protocol.JSON.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.config.AppConfig;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class WebPushSender {
    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    private static volatile PushService service;

    private WebPushSender() {}

    private static PushService service() throws GeneralSecurityException, JoseException {
        if (service != null) return service;
        synchronized (WebPushSender.class) {
            if (service != null) return service;
            AppConfig cfg = AppConfig.getInstance();
            String pub = cfg.getStringOrEmpty("webpush.vapid.public");
            String priv = cfg.getStringOrEmpty("webpush.vapid.private");
            String subject = cfg.getStringOrEmpty("webpush.vapid.subject");
            if (pub.isBlank() || priv.isBlank() || subject.isBlank()) {
                throw new IllegalStateException("webpush.vapid.* is not configured");
            }
            service = new PushService(pub, priv, subject);
            return service;
        }
    }

    public static boolean sendBase64Payload(String endpoint, String p256dhKey, String authKey, String payloadB64) {
        try {
            Subscription subscription = new Subscription(
                    endpoint,
                    new Subscription.Keys(p256dhKey, authKey)
            );
            byte[] payloadBytes = Base64.getDecoder().decode(payloadB64);
            Notification notification = new Notification(subscription, payloadBytes);
            var response = service().send(notification);
            int code = response.getStatusLine().getStatusCode();
            return code >= 200 && code < 300;
        } catch (NoSuchAlgorithmException e) {
            log.warn("WebPush crypto unsupported", e);
            return false;
        } catch (Exception e) {
            log.warn("WebPush send failed: {}", e.getMessage());
            return false;
        }
    }
}
