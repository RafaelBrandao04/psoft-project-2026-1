package pt.psoft.g1.psoftg1.readermanagement.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationRequestTest {

    @Test
    void testNotificationRequestCreationValid() {
        NotificationRequest request = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "912345678",
                "John Doe",
                "Book Lending Confirmation",
                "Your lending of 'Domain-Driven Design' is confirmed.",
                "LENDING_CREATED"
        );

        assertEquals("2026/1", request.readerNumber());
        assertEquals("reader@example.com", request.recipientEmail());
        assertEquals("912345678", request.recipientPhoneNumber());
        assertEquals("John Doe", request.recipientName());
        assertEquals("Book Lending Confirmation", request.subject());
        assertEquals("Your lending of 'Domain-Driven Design' is confirmed.", request.body());
        assertEquals("LENDING_CREATED", request.eventType());
    }

    @Test
    void testNotificationRequestNullChecks() {
        assertThrows(NullPointerException.class, () -> new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", null, "body", "LENDING_CREATED"
        ));
        assertThrows(NullPointerException.class, () -> new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", "subject", null, "LENDING_CREATED"
        ));
        assertThrows(NullPointerException.class, () -> new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", "subject", "body", null
        ));
    }
}
