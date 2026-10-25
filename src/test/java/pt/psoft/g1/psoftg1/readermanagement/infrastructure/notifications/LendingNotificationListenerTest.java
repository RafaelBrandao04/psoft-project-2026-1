package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingCreatedEvent;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingOverdueEvent;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingReturnedEvent;
import pt.psoft.g1.psoftg1.readermanagement.model.PhoneNumber;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;
import pt.psoft.g1.psoftg1.shared.model.Name;
import pt.psoft.g1.psoftg1.usermanagement.model.Reader;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Functional and Structural Tests for {@link LendingNotificationListener} (SUT = event listener).
 * Validates extraction of reader details, message composition for created/returned/overdue events,
 * and delegation to the notification port.
 */
class LendingNotificationListenerTest {

    private NotificationSender notificationSender;
    private ReaderRepository readerRepository;
    private LendingNotificationListener listener;

    @BeforeEach
    void setUp() {
        notificationSender = mock(NotificationSender.class);
        readerRepository = mock(ReaderRepository.class);
        listener = new LendingNotificationListener(notificationSender, readerRepository);
    }

    private ReaderDetails createMockReaderDetails(String username, String phone, String name) {
        ReaderDetails details = mock(ReaderDetails.class);
        Reader reader = mock(Reader.class);
        Name nameObj = mock(Name.class);

        when(details.getReader()).thenReturn(reader);
        when(reader.getUsername()).thenReturn(username);
        when(reader.getName()).thenReturn(nameObj);
        when(nameObj.toString()).thenReturn(name);

        when(details.getPhoneNumber()).thenReturn(phone);

        return details;
    }

    @Test
    @DisplayName("Should build NotificationRequest and delegate to sender when LendingCreatedEvent received")
    void testOnLendingCreated_Success() {
        ReaderDetails reader = createMockReaderDetails("reader@example.com", "912345678", "Alice Smith");
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.of(reader));

        LendingCreatedEvent event = new LendingCreatedEvent(
                "2026/10",
                "2026/1",
                "Refactoring",
                "9780201485677",
                LocalDate.of(2026, 11, 15)
        );

        listener.onLendingCreated(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender, times(1)).send(captor.capture());

        NotificationRequest req = captor.getValue();
        assertEquals("2026/1", req.readerNumber());
        assertEquals("reader@example.com", req.recipientEmail());
        assertEquals("912345678", req.recipientPhoneNumber());
        assertEquals("Alice Smith", req.recipientName());
        assertEquals("LENDING_CREATED", req.eventType());
        assertTrue(req.subject().contains("Refactoring"));
        assertTrue(req.body().contains("2026/10"));
        assertTrue(req.body().contains("2026-11-15"));
    }

    @Test
    @DisplayName("Should use fallback defaults if reader not found in repository")
    void testOnLendingCreated_ReaderNotFound() {
        when(readerRepository.findByReaderNumber("2026/99")).thenReturn(Optional.empty());

        LendingCreatedEvent event = new LendingCreatedEvent(
                "2026/10",
                "2026/99",
                "Refactoring",
                "9780201485677",
                LocalDate.of(2026, 11, 15)
        );

        listener.onLendingCreated(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender, times(1)).send(captor.capture());

        NotificationRequest req = captor.getValue();
        assertNull(req.recipientEmail());
        assertNull(req.recipientPhoneNumber());
        assertEquals("Reader", req.recipientName());
    }

    @Test
    @DisplayName("Should ignore null event without throwing")
    void testOnLendingCreated_NullEvent() {
        listener.onLendingCreated(null);
        verifyNoInteractions(notificationSender, readerRepository);
    }

    @Test
    @DisplayName("Should compose returned notification without fine when returned on time")
    void testOnLendingReturned_WithoutFine() {
        ReaderDetails reader = createMockReaderDetails("reader@example.com", "912345678", "Bob");
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.of(reader));

        LendingReturnedEvent event = new LendingReturnedEvent(
                "2026/10",
                "2026/1",
                "Domain-Driven Design",
                LocalDate.of(2026, 10, 24),
                0,
                0
        );

        listener.onLendingReturned(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender, times(1)).send(captor.capture());

        NotificationRequest req = captor.getValue();
        assertEquals("2026/1", req.readerNumber());
        assertEquals("reader@example.com", req.recipientEmail());
        assertEquals("912345678", req.recipientPhoneNumber());
        assertEquals("Bob", req.recipientName());
        assertEquals("LENDING_RETURNED", req.eventType());
        assertTrue(req.body().contains("successfully returned on 2026-10-24"));
        assertFalse(req.body().contains("fine"));
    }

    @Test
    @DisplayName("Should compose returned notification with fine when returned late")
    void testOnLendingReturned_WithFine() {
        ReaderDetails reader = createMockReaderDetails("reader@example.com", "912345678", "Bob");
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.of(reader));

        LendingReturnedEvent event = new LendingReturnedEvent(
                "2026/10",
                "2026/1",
                "Domain-Driven Design",
                LocalDate.of(2026, 10, 24),
                4,
                800
        );

        listener.onLendingReturned(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender, times(1)).send(captor.capture());

        NotificationRequest req = captor.getValue();
        assertEquals("2026/1", req.readerNumber());
        assertEquals("reader@example.com", req.recipientEmail());
        assertEquals("912345678", req.recipientPhoneNumber());
        assertEquals("Bob", req.recipientName());
        assertEquals("LENDING_RETURNED", req.eventType());
        assertTrue(req.body().contains("4 day(s) delay"));
        assertTrue(req.body().contains("8.00 EUR"));
    }

    @Test
    @DisplayName("Should format overdue warning notification with fine value")
    void testOnLendingOverdue() {
        ReaderDetails reader = createMockReaderDetails("reader@example.com", "912345678", "Charlie");
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.of(reader));

        LendingOverdueEvent event = new LendingOverdueEvent(
                "2026/10",
                "2026/1",
                "Clean Architecture",
                LocalDate.of(2026, 10, 10),
                14,
                2800
        );

        listener.onLendingOverdue(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender, times(1)).send(captor.capture());

        NotificationRequest req = captor.getValue();
        assertEquals("2026/1", req.readerNumber());
        assertEquals("reader@example.com", req.recipientEmail());
        assertEquals("912345678", req.recipientPhoneNumber());
        assertEquals("Charlie", req.recipientName());
        assertEquals("LENDING_OVERDUE", req.eventType());
        assertTrue(req.subject().contains("OVERDUE NOTICE"));
        assertTrue(req.body().contains("overdue by 14 day(s)"));
        assertTrue(req.body().contains("28.00 EUR"));
    }

    @Test
    @DisplayName("Should use default 'Reader' name when reader.getName() is null in onLendingReturned")
    void testOnLendingReturned_ReaderNameNull() {
        ReaderDetails reader = mock(ReaderDetails.class);
        Reader readerObj = mock(Reader.class);
        when(reader.getReader()).thenReturn(readerObj);
        when(readerObj.getUsername()).thenReturn("reader@example.com");
        when(readerObj.getName()).thenReturn(null);
        when(reader.getPhoneNumber()).thenReturn("912345678");
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.of(reader));

        LendingReturnedEvent event = new LendingReturnedEvent(
                "2026/10", "2026/1", "Title", LocalDate.now(), 0, 0
        );
        listener.onLendingReturned(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender).send(captor.capture());
        assertEquals("Reader", captor.getValue().recipientName());
    }

    @Test
    @DisplayName("Should use default 'Reader' name when reader.getName() is null in onLendingOverdue")
    void testOnLendingOverdue_ReaderNameNull() {
        ReaderDetails reader = mock(ReaderDetails.class);
        Reader readerObj = mock(Reader.class);
        when(reader.getReader()).thenReturn(readerObj);
        when(readerObj.getUsername()).thenReturn("reader@example.com");
        when(readerObj.getName()).thenReturn(null);
        when(reader.getPhoneNumber()).thenReturn("912345678");
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.of(reader));

        LendingOverdueEvent event = new LendingOverdueEvent(
                "2026/10", "2026/1", "Title", LocalDate.now().minusDays(2), 2, 400
        );
        listener.onLendingOverdue(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender).send(captor.capture());
        assertEquals("Reader", captor.getValue().recipientName());
    }

    @Test
    @DisplayName("Constructor should throw NPE when dependencies are null")
    void testConstructorNullChecks() {
        assertThrows(NullPointerException.class, () -> new LendingNotificationListener(null, readerRepository));
        assertThrows(NullPointerException.class, () -> new LendingNotificationListener(notificationSender, null));
    }
}
