package com.balancify.backend.service;

import com.balancify.backend.config.WebPushProperties;
import com.balancify.backend.domain.PushSubscription;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends Web Push messages. It is on only when VAPID keys and a subject are configured; endpoints
 * are limited to the browsers' push services, so a stored subscription cannot aim requests anywhere else.
 */
@Service
public class WebPushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebPushService.class);
    // Push services keep an undelivered message this long, in seconds.
    private static final String TIME_TO_LIVE = "43200";
    private static final List<String> PUSH_SERVICE_HOSTS = List.of(
        "fcm.googleapis.com",
        "android.googleapis.com",
        "updates.push.services.mozilla.com"
    );
    private static final List<String> PUSH_SERVICE_HOST_SUFFIXES = List.of(".push.apple.com", ".notify.windows.com");

    private final WebPushCrypto.VapidKeys keys;
    private final String subject;
    private final HttpClient httpClient;

    public WebPushService(WebPushProperties properties) {
        this.subject = properties.getSubject();
        this.keys = loadKeys(properties);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isEnabled() {
        return keys != null;
    }

    /** The key browsers subscribe with; null while push is off. */
    public String publicKey() {
        return keys == null ? null : keys.publicKeyBase64();
    }

    public static boolean isAllowedEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() > 1000) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getUserInfo() == null
                && (PUSH_SERVICE_HOSTS.contains(host) || PUSH_SERVICE_HOST_SUFFIXES.stream().anyMatch(host::endsWith));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Sends one message and returns the push service's status code, or -1 when it could not be sent.
     * 404 and 410 mean the subscription is gone.
     */
    public int send(PushSubscription subscription, byte[] payload) {
        if (keys == null || !isAllowedEndpoint(subscription.getEndpoint())) {
            return -1;
        }
        try {
            byte[] body = WebPushCrypto.encrypt(payload, subscription.getP256dh(), subscription.getAuth());
            HttpRequest request = HttpRequest.newBuilder(URI.create(subscription.getEndpoint()))
                .timeout(Duration.ofSeconds(10))
                .header("TTL", TIME_TO_LIVE)
                .header("Urgency", "normal")
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", WebPushCrypto.vapidAuthorization(subscription.getEndpoint(), subject, keys, Instant.now()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception exception) {
            LOGGER.warn("Web Push send failed: {}", exception.getClass().getSimpleName());
            return -1;
        }
    }

    private WebPushCrypto.VapidKeys loadKeys(WebPushProperties properties) {
        if (properties.getVapidPublicKey().isEmpty() || properties.getVapidPrivateKey().isEmpty()) {
            return null;
        }
        if (!subject.startsWith("mailto:") && !subject.startsWith("https://")) {
            LOGGER.warn("Web Push is off: the subject must start with mailto: or https://");
            return null;
        }
        try {
            return WebPushCrypto.vapidKeys(properties.getVapidPublicKey(), properties.getVapidPrivateKey());
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Web Push is off: the VAPID keys are not usable");
            return null;
        }
    }
}
