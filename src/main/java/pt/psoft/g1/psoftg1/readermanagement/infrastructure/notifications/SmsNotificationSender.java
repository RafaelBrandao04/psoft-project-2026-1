package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component("sms")
public class SmsNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationSender.class);
    public static final String CHANNEL_NAME = "sms";
    public static final int MAX_SMS_LENGTH = 160;

    private final AtomicInteger sentCount = new AtomicInteger(0);

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

        log.info("[SMS-CHANNEL] Successfully dispatched SMS to '{}' | Event: '{}' | Length: {} chars",
                phoneNumber, request.eventType(), smsText.length());
        log.debug("[SMS-CHANNEL] SMS Body: '{}'", smsText);

        sentCount.incrementAndGet();
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

    public int getSentCount() {
        return sentCount.get();
    }

    public void resetSentCount() {
        sentCount.set(0);
    }
}
