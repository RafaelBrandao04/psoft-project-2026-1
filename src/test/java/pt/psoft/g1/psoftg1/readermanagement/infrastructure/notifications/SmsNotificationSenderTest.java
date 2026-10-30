package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class SmsNotificationSenderTest {

    private SmsNotificationSender smsSender;
    private WebClient webClient;
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    private WebClient.RequestBodySpec requestBodySpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        smsSender = new SmsNotificationSender();
        webClient = mock(WebClient.class);
        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(WebClient.RequestBodySpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);
    }

    @Test
    void testChannelName() {
        assertEquals("sms", smsSender.getChannelName());
    }

    @Test
    void testSendSuccessInStandaloneMode() {
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
        assertEquals(0, smsSender.getFailureCount());
    }

    @Test
    void testSendSuccessWithBeeceptorWebClient() {
        SmsNotificationSender sender = new SmsNotificationSender(
                webClient,
                "https://psoft-g1-sms-gateway.free.beeceptor.com/api/v1/sms/send",
                2000L
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        NotificationRequest request = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "912345678",
                "Jane Doe",
                "Book Returned",
                "Your return was processed successfully.",
                "LENDING_RETURNED"
        );

        assertDoesNotThrow(() -> sender.send(request));
        assertEquals(1, sender.getSentCount());
        assertEquals(0, sender.getFailureCount());
        assertEquals("https://psoft-g1-sms-gateway.free.beeceptor.com/api/v1/sms/send", sender.getGatewayUrl());
    }

    @Test
    void testSendFailureWithBeeceptorIsFaultIsolated() {
        SmsNotificationSender sender = new SmsNotificationSender(
                webClient,
                "https://psoft-g1-sms-gateway.free.beeceptor.com/api/v1/sms/send",
                2000L
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new WebClientResponseException(
                503, "Service Unavailable", null, null, null
        )));

        NotificationRequest request = new NotificationRequest(
                "2026/1",
                "reader@example.com",
                "912345678",
                "Jane Doe",
                "Book Returned",
                "Your return was processed successfully.",
                "LENDING_RETURNED"
        );

        // Fault isolation (ASR06): Must NOT throw exception to caller
        assertDoesNotThrow(() -> sender.send(request));
        assertEquals(0, sender.getSentCount());
        assertEquals(1, sender.getFailureCount());
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
        smsSender.resetMetrics();
        assertEquals(0, smsSender.getSentCount());
        assertEquals(0, smsSender.getFailureCount());
    }
}
