package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;

import static org.junit.jupiter.api.Assertions.*;

class SmsNotificationSenderTest {

    private SmsNotificationSender smsSender;

    @BeforeEach
    void setUp() {
        smsSender = new SmsNotificationSender();
    }

    @Test
    void testChannelName() {
        assertEquals("sms", smsSender.getChannelName());
    }

    @Test
    void testSendSuccess() {
        NotificationRequest request = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "912345678",
                "Jane Doe",
                "Book Returned",
                "Your return was processed successfully.",
                "LENDING_RETURNED"
        );

        smsSender.send(request);
        assertEquals(1, smsSender.getSentCount());
    }

    @Test
    void testSendWithBlankPhoneNumberSkipsWithoutCrashing() {
        NotificationRequest requestNullPhone = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                null,
                "Jane Doe",
                "Book Returned",
                "Body",
                "LENDING_RETURNED"
        );
        smsSender.send(requestNullPhone);
        assertEquals(0, smsSender.getSentCount());

        NotificationRequest requestEmptyPhone = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "   ",
                "Jane Doe",
                "Book Returned",
                "Body",
                "LENDING_RETURNED"
        );
        smsSender.send(requestEmptyPhone);
        assertEquals(0, smsSender.getSentCount());
    }

    @Test
    void testSendNullRequestThrowsException() {
        assertThrows(NullPointerException.class, () -> smsSender.send(null));
    }

    @Test
    void testFormatSmsContentTruncation() {
        assertEquals("", smsSender.formatSmsContent(null));

        String shortMessage = "Short message within 160 characters";
        assertEquals(shortMessage, smsSender.formatSmsContent(shortMessage));

        String exact160 = "A".repeat(160);
        assertEquals(exact160, smsSender.formatSmsContent(exact160));

        String longMessage = "A".repeat(170);
        String formatted = smsSender.formatSmsContent(longMessage);
        assertEquals(160, formatted.length());
        assertTrue(formatted.endsWith("..."));
        assertEquals("A".repeat(157) + "...", formatted);
    }

    @Test
    void testResetMetrics() {
        NotificationRequest request = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "912345678",
                "Jane Doe",
                "Book Returned",
                "Your return was processed successfully.",
                "LENDING_RETURNED"
        );
        smsSender.send(request);
        assertEquals(1, smsSender.getSentCount());
        smsSender.resetSentCount();
        assertEquals(0, smsSender.getSentCount());
    }
}
