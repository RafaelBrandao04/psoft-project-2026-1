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
class WebhookNotificationSenderTest {

    private WebClient webClient;
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    private WebClient.RequestBodySpec requestBodySpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        webClient = mock(WebClient.class);
        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(WebClient.RequestBodySpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);
    }

    @Test
    void testChannelName() {
        WebhookNotificationSender sender = new WebhookNotificationSender(webClient, "http://localhost:8080/webhook");
        assertEquals("webhook", sender.getChannelName());
        assertEquals("http://localhost:8080/webhook", sender.getWebhookUrl());
        assertEquals(3000L, sender.getTimeoutMs());
    }

    @Test
    void testSendNullRequestThrowsException() {
        WebhookNotificationSender sender = new WebhookNotificationSender(webClient, "http://localhost:8080/webhook");
        assertThrows(NullPointerException.class, () -> sender.send(null));
    }

    @Test
    void testSendWithEmptyUrlSkipsGracefully() {
        WebhookNotificationSender sender = new WebhookNotificationSender(webClient, "");
        NotificationRequest request = new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", "Subject", "Body", "EVENT"
        );
        sender.send(request);
        assertEquals(0, sender.getSentCount());
        assertEquals(0, sender.getFailureCount());
        verifyNoInteractions(webClient);
    }

    @Test
    void testSendSuccess() {
        WebhookNotificationSender sender = new WebhookNotificationSender(webClient, "http://localhost:8080/webhook", 2000L);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        NotificationRequest request = new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", "Subject", "Body", "EVENT"
        );

        assertDoesNotThrow(() -> sender.send(request));
        assertEquals(1, sender.getSentCount());
        assertEquals(0, sender.getFailureCount());
    }

    @Test
    void testSendFailureIsFaultIsolatedAndDoesNotThrow() {
        WebhookNotificationSender sender = new WebhookNotificationSender(webClient, "http://localhost:8080/webhook", 2000L);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new WebClientResponseException(
                500, "Internal Server Error", null, null, null
        )));

        NotificationRequest request = new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", "Subject", "Body", "EVENT"
        );

        // Fault isolation (ASR06): Must NOT throw exception to caller
        assertDoesNotThrow(() -> sender.send(request));
        assertEquals(0, sender.getSentCount());
        assertEquals(1, sender.getFailureCount());
    }

    @Test
    void testConstructorWithInvalidTimeoutDefaults() {
        WebhookNotificationSender senderZero = new WebhookNotificationSender(webClient, "http://localhost:8080", 0L);
        assertEquals(3000L, senderZero.getTimeoutMs());

        WebhookNotificationSender senderNegative = new WebhookNotificationSender(webClient, "http://localhost:8080", -50L);
        assertEquals(3000L, senderNegative.getTimeoutMs());
    }

    @Test
    void testResetMetrics() {
        WebhookNotificationSender sender = new WebhookNotificationSender(webClient, "http://localhost:8080/webhook", 2000L);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        NotificationRequest request = new NotificationRequest(
                "2026/1", "reader@example.com", "912345678", "John", "Subject", "Body", "EVENT"
        );
        sender.send(request);
        assertEquals(1, sender.getSentCount());

        sender.resetMetrics();
        assertEquals(0, sender.getSentCount());
        assertEquals(0, sender.getFailureCount());
    }
}
