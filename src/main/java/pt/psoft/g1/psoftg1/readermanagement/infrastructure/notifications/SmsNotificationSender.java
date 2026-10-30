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

@Component("sms")
public class SmsNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationSender.class);
    public static final String CHANNEL_NAME = "sms";
    public static final int MAX_SMS_LENGTH = 160;

    private final WebClient webClient;
    private final String gatewayUrl;
    private final long timeoutMs;

    private final AtomicInteger sentCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    public SmsNotificationSender() {
        this(null, "", 3000L);
    }

    public SmsNotificationSender(WebClient webClient, String gatewayUrl) {
        this(webClient, gatewayUrl, 3000L);
    }

    @Autowired
    public SmsNotificationSender(
            @Value("${notification.sms.gateway-url:https://psoft-g1-sms-gateway.free.beeceptor.com/api/v1/sms/send}") String gatewayUrl,
            @Value("${notification.sms.timeout-ms:3000}") long timeoutMs) {
        this(WebClient.builder().build(), gatewayUrl, timeoutMs);
    }

    public SmsNotificationSender(WebClient webClient, String gatewayUrl, long timeoutMs) {
        this.webClient = webClient;
        this.gatewayUrl = gatewayUrl != null ? gatewayUrl.trim() : "";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000L;
    }

    @Override
    public void send(NotificationRequest request) {
        Objects.requireNonNull(request, "NotificationRequest must not be null");

        String phoneNumber = request.recipientPhoneNumber();
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            log.warn("[SMS-CHANNEL] Skipped sending SMS: reader '{}' has no registered phone number.",
                    request.readerNumber());
            return;
        }

        String rawContent = String.format("[LMS] %s: %s", request.subject(), request.body());
        String smsText = formatSmsContent(rawContent);

        int characterCount = smsText.length();
        int segments = (int) Math.ceil((double) characterCount / MAX_SMS_LENGTH);

        SmsGatewayPayload payload = new SmsGatewayPayload(
                phoneNumber,
                "LMS-Library",
                smsText,
                characterCount,
                segments > 0 ? segments : 1,
                request.eventType(),
                request.readerNumber(),
                Instant.now().toString()
        );

        if (webClient != null && !gatewayUrl.isEmpty()) {
            try {
                log.info("[SMS-CHANNEL] Dispatching SMS payload to Gateway URL '{}' for phone '{}' (chars: {}, parts: {})",
                        gatewayUrl, phoneNumber, characterCount, segments);

                webClient.post()
                        .uri(gatewayUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .toBodilessEntity()
                        .block(Duration.ofMillis(timeoutMs));

                log.info("[SMS-CHANNEL] Successfully dispatched SMS to '{}' via Beeceptor/Gateway | Event: '{}'",
                        phoneNumber, request.eventType());
                sentCount.incrementAndGet();
            } catch (WebClientException | IllegalStateException ex) {
                failureCount.incrementAndGet();
                log.error("[SMS-CHANNEL] Failed to deliver SMS to Gateway '{}': {}", gatewayUrl, ex.getMessage());
            }
        } else {
            // Standalone / Simulation mode without active WebClient or URL configured
            log.info("[SMS-CHANNEL] Successfully dispatched SMS to '{}' | Event: '{}' | Length: {} chars",
                    phoneNumber, request.eventType(), characterCount);
            log.debug("[SMS-CHANNEL] SMS Body: '{}'", smsText);
            sentCount.incrementAndGet();
        }
    }

    public String formatSmsContent(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_SMS_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_SMS_LENGTH - 3) + "...";
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
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

    public void resetSentCount() {
        sentCount.set(0);
    }

    public void resetMetrics() {
        sentCount.set(0);
        failureCount.set(0);
    }

    public record SmsGatewayPayload(
            String to,
            String from,
            String message,
            int characterCount,
            int parts,
            String eventType,
            String readerNumber,
            String timestamp
    ) {}
}
