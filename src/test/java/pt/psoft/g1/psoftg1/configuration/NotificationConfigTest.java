package pt.psoft.g1.psoftg1.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications.CompositeNotificationSender;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Functional and Structural Tests for {@link NotificationConfig} (SUT = configuration class).
 *
 * <p>Validates setup-time channel selection, combinations, priority ordering, deduplication,
 * and dynamic reflective resolution for strict Open-Closed Principle (OCP) adherence.</p>
 */
class NotificationConfigTest {

    private final NotificationConfig config = new NotificationConfig();

    public static class TestDummySender implements NotificationSender {
        @Override
        public void send(NotificationRequest request) {}

        @Override
        public String getChannelName() {
            return "testdummy";
        }
    }

    @Test
    @DisplayName("Should resolve channels in the exact order configured at setup time")
    void testResolveSenders_ConfiguredOrderPreserved() {
        NotificationSender emailMock = mock(NotificationSender.class);
        when(emailMock.getChannelName()).thenReturn("email");

        NotificationSender smsMock = mock(NotificationSender.class);
        when(smsMock.getChannelName()).thenReturn("sms");

        NotificationSender webhookMock = mock(NotificationSender.class);
        when(webhookMock.getChannelName()).thenReturn("webhook");

        List<NotificationSender> available = List.of(emailMock, smsMock, webhookMock);

        // Combination 1: webhook first, then email
        List<NotificationSender> result1 = config.resolveSenders(List.of("webhook", "email"), available);
        assertEquals(2, result1.size());
        assertSame(webhookMock, result1.get(0));
        assertSame(emailMock, result1.get(1));

        // Combination 2: sms only (individual selection)
        List<NotificationSender> result2 = config.resolveSenders(List.of("sms"), available);
        assertEquals(1, result2.size());
        assertSame(smsMock, result2.get(0));
    }

    @Test
    @DisplayName("Should deduplicate repeated channel tokens")
    void testResolveSenders_Deduplicates() {
        NotificationSender emailMock = mock(NotificationSender.class);
        when(emailMock.getChannelName()).thenReturn("email");

        List<NotificationSender> result = config.resolveSenders(List.of("email", "email"), List.of(emailMock));
        assertEquals(1, result.size());
        assertSame(emailMock, result.get(0));
    }

    @Test
    @DisplayName("Should match channel tokens case-insensitively")
    void testResolveSenders_CaseInsensitive() {
        NotificationSender emailMock = mock(NotificationSender.class);
        when(emailMock.getChannelName()).thenReturn("email");

        List<NotificationSender> result = config.resolveSenders(List.of("EMAIL"), List.of(emailMock));
        assertEquals(1, result.size());
        assertSame(emailMock, result.get(0));
    }

    @Test
    @DisplayName("Should handle null and empty channel lists gracefully")
    void testResolveSenders_NullOrEmpty() {
        assertTrue(config.resolveSenders(null, Collections.emptyList()).isEmpty());
        assertTrue(config.resolveSenders(Collections.emptyList(), Collections.emptyList()).isEmpty());
        assertTrue(config.resolveSenders(List.of(""), Collections.emptyList()).isEmpty());
        assertTrue(config.resolveSenders(List.of("   "), Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("Should ignore CompositeNotificationSender instances during resolution")
    void testResolveSenders_SkipsCompositeInstances() {
        CompositeNotificationSender composite = new CompositeNotificationSender(Collections.emptyList());
        NotificationSender emailMock = mock(NotificationSender.class);
        when(emailMock.getChannelName()).thenReturn("email");

        List<NotificationSender> result = config.resolveSenders(List.of("email"), List.of(composite, emailMock));
        assertEquals(1, result.size());
        assertSame(emailMock, result.get(0));
    }

    @Test
    @DisplayName("Should dynamically instantiate new channel by fully-qualified class name (OCP)")
    void testResolveSenders_DynamicClassNameInstantiation() {
        String className = TestDummySender.class.getName();
        List<NotificationSender> result = config.resolveSenders(List.of(className), Collections.emptyList());

        assertEquals(1, result.size());
        assertEquals("testdummy", result.get(0).getChannelName());
    }

    @Test
    @DisplayName("Should ignore unresolvable class names gracefully")
    void testResolveSenders_UnresolvableToken_IgnoredGracefully() {
        List<NotificationSender> result = config.resolveSenders(List.of("com.nonexistent.UnknownSender"), Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("notificationSender bean creation should return CompositeNotificationSender with configured channels")
    void testNotificationSenderBeanCreation() {
        NotificationSender emailMock = mock(NotificationSender.class);
        when(emailMock.getChannelName()).thenReturn("email");

        ReflectionTestUtils.setField(config, "configuredChannels", List.of("email"));

        NotificationSender bean = config.notificationSender(List.of(emailMock));
        assertNotNull(bean);
        assertTrue(bean instanceof CompositeNotificationSender);
        assertEquals("composite(email)", bean.getChannelName());
    }
}
