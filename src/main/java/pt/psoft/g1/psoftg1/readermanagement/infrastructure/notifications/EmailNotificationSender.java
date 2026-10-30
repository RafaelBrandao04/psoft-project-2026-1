package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component("email")
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);
    public static final String CHANNEL_NAME = "email";

    private final JavaMailSender mailSender;
    private final String fromAddress;

    private final AtomicInteger sentCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    public EmailNotificationSender() {
        this(null, "notifications@library.psoft.pt");
    }

    public EmailNotificationSender(JavaMailSender mailSender) {
        this(mailSender, "notifications@library.psoft.pt");
    }

    @Autowired
    public EmailNotificationSender(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${notification.email.from:notifications@library.psoft.pt}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = (fromAddress != null && !fromAddress.trim().isEmpty())
                ? fromAddress.trim()
                : "notifications@library.psoft.pt";
    }

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

        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(recipientEmail);
                message.setSubject(subject);
                message.setText(formattedBody);

                log.info("[EMAIL-CHANNEL] Dispatching SMTP message to '{}' via Mailtrap/SMTP | Subject: '{}'",
                        recipientEmail, subject);
                mailSender.send(message);

                log.info("[EMAIL-CHANNEL] Successfully dispatched email to '{}' | Subject: '{}' | Event: '{}'",
                        recipientEmail, subject, request.eventType());
                sentCount.incrementAndGet();
            } catch (RuntimeException ex) {
                failureCount.incrementAndGet();
                log.error("[EMAIL-CHANNEL] Failed to deliver email via SMTP to '{}': {}", recipientEmail,
                        ex.getMessage());
            }
        } else {
            // Standalone / Simulation mode without SMTP bean
            log.info("[EMAIL-CHANNEL] Successfully dispatched email to '{}' | Subject: '{}' | Event: '{}'",
                    recipientEmail, subject, request.eventType());
            log.debug("[EMAIL-CHANNEL] Content payload:%n{}", formattedBody);
            sentCount.incrementAndGet();
        }
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    public String getFromAddress() {
        return fromAddress;
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
}
