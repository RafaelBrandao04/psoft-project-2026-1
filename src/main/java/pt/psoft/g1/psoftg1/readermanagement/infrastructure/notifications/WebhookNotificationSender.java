package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component("webhook")
public class WebhookNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationSender.class);
    public static final String CHANNEL_NAME = "webhook";

    private final WebClient webClient;
    private final String webhookUrl;
    private final long timeoutMs;

    private final AtomicInteger sentCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    @Autowired
    public WebhookNotificationSender(
            @Value("${notification.webhook.url:http://localhost:8080/api/webhook/reader}") String webhookUrl,
            @Value("${notification.webhook.timeout-ms:3000}") long timeoutMs) {
        this(WebClient.builder().build(), webhookUrl, timeoutMs);
    }

    public WebhookNotificationSender(WebClient webClient, String webhookUrl) {
        this(webClient, webhookUrl, 3000L);
    }

    public WebhookNotificationSender(WebClient webClient, String webhookUrl, long timeoutMs) {
        this.webClient = Objects.requireNonNull(webClient, "WebClient must not be null");
        this.webhookUrl = webhookUrl != null ? webhookUrl.trim() : "";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000L;
    }

    @Override
    public void send(NotificationRequest request) {
        Objects.requireNonNull(request, "NotificationRequest must not be null");

        if (webhookUrl.isEmpty()) {
            log.warn("[WEBHOOK-CHANNEL] Skipped dispatch: no target webhook URL is configured.");
            return;
        }

        WebhookPayload payload = new WebhookPayload(
                request.eventType(),
                request.readerNumber(),
                request.recipientEmail(),
                request.recipientPhoneNumber(),
                request.recipientName(),
                request.subject(),
                request.body(),
                Instant.now().toString());

        try {
            log.info("[WEBHOOK-CHANNEL] Dispatching notification to URL '{}' for reader '{}' (event: '{}')",
                    webhookUrl, request.readerNumber(), request.eventType());

            webClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(timeoutMs));

            log.info("[WEBHOOK-CHANNEL] Successfully delivered webhook payload to '{}'", webhookUrl);
            sentCount.incrementAndGet();
        } catch (WebClientException | IllegalStateException e) {
            failureCount.incrementAndGet();
            log.error("[WEBHOOK-CHANNEL] Failed to deliver webhook notification to '{}': {}", webhookUrl,
                    e.getMessage());
        }
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public int getSentCount() {
        return sentCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public void resetMetrics() {
        sentCount.set(0);
        failureCount.set(0);
    }

    /**
     * JSON payload structure sent to external webhook listeners.
     */
    public record WebhookPayload(
            String eventType,
            String readerNumber,
            String recipientEmail,
            String recipientPhoneNumber,
            String recipientName,
            String subject,
            String body,
            String timestamp) {
    }
}
