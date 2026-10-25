package pt.psoft.g1.psoftg1.readermanagement.services;

import java.util.Objects;

public record NotificationRequest(
        String readerNumber,
        String recipientEmail,
        String recipientPhoneNumber,
        String recipientName,
        String subject,
        String body,
        String eventType) {
    public NotificationRequest {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
    }
}
