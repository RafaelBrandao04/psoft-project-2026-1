package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;

import static org.junit.jupiter.api.Assertions.*;

class EmailNotificationSenderTest {

    private EmailNotificationSender emailSender;

    @BeforeEach
    void setUp() {
        emailSender = new EmailNotificationSender();
    }

    @Test
    void testChannelName() {
        assertEquals("email", emailSender.getChannelName());
    }

    @Test
    void testSendSuccess() {
        NotificationRequest request = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "912345678",
                "John Doe",
                "Lending Created",
                "Your lending for 'Clean Code' is active.",
                "LENDING_CREATED"
        );

        emailSender.send(request);
        assertEquals(1, emailSender.getSentCount());
    }

    @Test
    void testSendWithBlankEmailSkipsWithoutCrashing() {
        NotificationRequest requestNullEmail = new NotificationRequest(
                "2026/1",
                null,
                "912345678",
                "John Doe",
                "Lending Created",
                "Your lending is active.",
                "LENDING_CREATED"
        );
        emailSender.send(requestNullEmail);
        assertEquals(0, emailSender.getSentCount());

        NotificationRequest requestEmptyEmail = new NotificationRequest(
                "2026/1",
                "   ",
                "912345678",
                "John Doe",
                "Lending Created",
                "Your lending is active.",
                "LENDING_CREATED"
        );
        emailSender.send(requestEmptyEmail);
        assertEquals(0, emailSender.getSentCount());
    }

    @Test
    void testSendNullRequestThrowsException() {
        assertThrows(NullPointerException.class, () -> emailSender.send(null));
    }

    @Test
    void testResetMetrics() {
        NotificationRequest request = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "912345678",
                "John Doe",
                "Subject",
                "Body",
                "EVENT"
        );
        emailSender.send(request);
        assertEquals(1, emailSender.getSentCount());
        emailSender.resetSentCount();
        assertEquals(0, emailSender.getSentCount());
    }
}
