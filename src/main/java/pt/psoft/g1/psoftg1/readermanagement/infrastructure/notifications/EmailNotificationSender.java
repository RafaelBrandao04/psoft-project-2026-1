package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component("email")
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);
    public static final String CHANNEL_NAME = "email";

    private final AtomicInteger sentCount = new AtomicInteger(0);

    @Override
    public void send(NotificationRequest request) {
        Objects.requireNonNull(request, "NotificationRequest must not be null");

        String recipientEmail = request.recipientEmail();
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            log.warn("[EMAIL-CHANNEL] Skipped sending email: reader '{}' has no valid email address.",
                    request.readerNumber());
            return;
        }

        String subject = "[LMS Library] " + request.subject();
        String formattedBody = String.format("Dear %s,%n%n%s%n%nKind regards,%nLibrary Management System",
                request.recipientName() != null ? request.recipientName() : "Reader",
                request.body());

        log.info("[EMAIL-CHANNEL] Successfully dispatched email to '{}' | Subject: '{}' | Event: '{}'",
                recipientEmail, subject, request.eventType());
        log.debug("[EMAIL-CHANNEL] Content payload:%n{}", formattedBody);

        sentCount.incrementAndGet();
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
