package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.junit.jupiter.api.Test;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompositeNotificationSenderTest {

    @Test
    void testConstructorHandlesNullAndFiltersNulls() {
        CompositeNotificationSender emptyComposite = new CompositeNotificationSender(null);
        assertEquals(0, emptyComposite.getActiveChannelCount());
        assertEquals("composite(none)", emptyComposite.getChannelName());

        NotificationSender sender1 = mock(NotificationSender.class);
        when(sender1.getChannelName()).thenReturn("email");

        CompositeNotificationSender filteredComposite = new CompositeNotificationSender(Arrays.asList(sender1, null));
        assertEquals(1, filteredComposite.getActiveChannelCount());
        assertEquals("composite(email)", filteredComposite.getChannelName());
    }

    @Test
    void testChannelNameWithMultipleChannels() {
        NotificationSender email = mock(NotificationSender.class);
        when(email.getChannelName()).thenReturn("email");
        NotificationSender sms = mock(NotificationSender.class);
        when(sms.getChannelName()).thenReturn("sms");
        NotificationSender webhook = mock(NotificationSender.class);
        when(webhook.getChannelName()).thenReturn("webhook");

        CompositeNotificationSender composite = new CompositeNotificationSender(List.of(email, sms, webhook));
        assertEquals("composite(email,sms,webhook)", composite.getChannelName());
        assertEquals(3, composite.getActiveChannelCount());
    }

    @Test
    void testSendNullRequestThrowsException() {
        CompositeNotificationSender composite = new CompositeNotificationSender(Collections.emptyList());
        assertThrows(NullPointerException.class, () -> composite.send(null));
    }

    @Test
    void testSendEmptySendersDoesNotCrash() {
        CompositeNotificationSender composite = new CompositeNotificationSender(Collections.emptyList());
        NotificationRequest request = new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", "Subject", "Body", "EVENT"
        );
        assertDoesNotThrow(() -> composite.send(request));
    }

    @Test
    void testSendDispatchesToAllActiveSenders() {
        NotificationSender email = mock(NotificationSender.class);
        NotificationSender sms = mock(NotificationSender.class);
        NotificationSender webhook = mock(NotificationSender.class);

        CompositeNotificationSender composite = new CompositeNotificationSender(List.of(email, sms, webhook));
        NotificationRequest request = new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", "Subject", "Body", "EVENT"
        );

        composite.send(request);

        verify(email, times(1)).send(request);
        verify(sms, times(1)).send(request);
        verify(webhook, times(1)).send(request);
    }

    @Test
    void testFaultIsolationOneSenderThrowsDoesNotAbortOthers() {
        NotificationSender email = mock(NotificationSender.class);
        NotificationSender failingWebhook = mock(NotificationSender.class);
        when(failingWebhook.getChannelName()).thenReturn("webhook");
        NotificationSender sms = mock(NotificationSender.class);

        NotificationRequest request = new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", "Subject", "Body", "EVENT"
        );

        // Simulate external network exception in webhook
        doThrow(new RuntimeException("Connection timed out to external webhook server"))
                .when(failingWebhook).send(request);

        CompositeNotificationSender composite = new CompositeNotificationSender(List.of(email, failingWebhook, sms));

        // Tactic: Fault Isolation (ASR06) - Calling send MUST NOT throw or interrupt other channels
        assertDoesNotThrow(() -> composite.send(request));

        // Both email and sms MUST have executed despite webhook failure
        verify(email, times(1)).send(request);
        verify(failingWebhook, times(1)).send(request);
        verify(sms, times(1)).send(request);
    }
}
