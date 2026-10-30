package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    void testSendSuccessInStandaloneMode() {
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
        assertEquals(0, emailSender.getFailureCount());
    }

    @Test
    void testSendSuccessWithJavaMailSender() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        EmailNotificationSender senderWithSmtp = new EmailNotificationSender(mockMailSender, "library@example.com");

        NotificationRequest request = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "912345678",
                "John Doe",
                "Lending Created",
                "Your lending is active.",
                "LENDING_CREATED"
        );

        senderWithSmtp.send(request);

        assertEquals(1, senderWithSmtp.getSentCount());
        assertEquals(0, senderWithSmtp.getFailureCount());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertEquals("library@example.com", sentMessage.getFrom());
        assertNotNull(sentMessage.getTo());
        assertEquals("reader@example.com", sentMessage.getTo()[0]);
        assertTrue(sentMessage.getSubject().contains("Lending Created"));
        assertTrue(sentMessage.getText().contains("Dear John Doe"));
    }

    @Test
    void testSendFailureWithJavaMailSenderIsFaultIsolated() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP connection refused to Mailtrap"))
                .when(mockMailSender).send(any(SimpleMailMessage.class));

        EmailNotificationSender senderWithSmtp = new EmailNotificationSender(mockMailSender, "library@example.com");

        NotificationRequest request = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "912345678",
                "John Doe",
                "Lending Created",
                "Your lending is active.",
                "LENDING_CREATED"
        );

        // Fault isolation (ASR06): Must NOT throw exception to caller
        assertDoesNotThrow(() -> senderWithSmtp.send(request));
        assertEquals(0, senderWithSmtp.getSentCount());
        assertEquals(1, senderWithSmtp.getFailureCount());
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
        emailSender.resetMetrics();
        assertEquals(0, emailSender.getSentCount());
        assertEquals(0, emailSender.getFailureCount());
    }
}
