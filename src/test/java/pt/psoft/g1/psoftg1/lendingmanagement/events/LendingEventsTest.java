package pt.psoft.g1.psoftg1.lendingmanagement.events;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LendingEventsTest {

    @Test
    void testLendingCreatedEventValid() {
        LocalDate limitDate = LocalDate.now().plusDays(14);
        LendingCreatedEvent event = new LendingCreatedEvent("2026/1", "2026/12", "Domain-Driven Design", "9780321125217", limitDate);

        assertEquals("2026/1", event.lendingNumber());
        assertEquals("2026/12", event.readerNumber());
        assertEquals("Domain-Driven Design", event.bookTitle());
        assertEquals("9780321125217", event.isbn());
        assertEquals(limitDate, event.limitDate());
    }

    @Test
    void testLendingCreatedEventNullChecks() {
        LocalDate limitDate = LocalDate.now().plusDays(14);
        assertThrows(NullPointerException.class, () -> new LendingCreatedEvent(null, "2026/12", "DDD", "9780321125217", limitDate));
        assertThrows(NullPointerException.class, () -> new LendingCreatedEvent("2026/1", null, "DDD", "9780321125217", limitDate));
        assertThrows(NullPointerException.class, () -> new LendingCreatedEvent("2026/1", "2026/12", null, "9780321125217", limitDate));
        assertThrows(NullPointerException.class, () -> new LendingCreatedEvent("2026/1", "2026/12", "DDD", null, limitDate));
        assertThrows(NullPointerException.class, () -> new LendingCreatedEvent("2026/1", "2026/12", "DDD", "9780321125217", null));
    }

    @Test
    void testLendingReturnedEventValid() {
        LocalDate returnedDate = LocalDate.now();
        LendingReturnedEvent eventWithoutFine = new LendingReturnedEvent("2026/1", "2026/12", "Domain-Driven Design", returnedDate, 0, 0);

        assertEquals("2026/1", eventWithoutFine.lendingNumber());
        assertEquals("2026/12", eventWithoutFine.readerNumber());
        assertEquals("Domain-Driven Design", eventWithoutFine.bookTitle());
        assertEquals(returnedDate, eventWithoutFine.returnedDate());
        assertEquals(0, eventWithoutFine.daysDelayed());
        assertFalse(eventWithoutFine.hasFine());

        LendingReturnedEvent eventWithFine = new LendingReturnedEvent("2026/1", "2026/12", "Domain-Driven Design", returnedDate, 3, 600);
        assertTrue(eventWithFine.hasFine());
        assertEquals(600, eventWithFine.fineValueInCents());
    }

    @Test
    void testLendingReturnedEventNullChecks() {
        LocalDate returnedDate = LocalDate.now();
        assertThrows(NullPointerException.class, () -> new LendingReturnedEvent(null, "2026/12", "DDD", returnedDate, 0, null));
        assertThrows(NullPointerException.class, () -> new LendingReturnedEvent("2026/1", null, "DDD", returnedDate, 0, null));
        assertThrows(NullPointerException.class, () -> new LendingReturnedEvent("2026/1", "2026/12", null, returnedDate, 0, null));
        assertThrows(NullPointerException.class, () -> new LendingReturnedEvent("2026/1", "2026/12", "DDD", null, 0, null));
    }

    @Test
    void testLendingOverdueEventValid() {
        LocalDate limitDate = LocalDate.now().minusDays(5);
        LendingOverdueEvent event = new LendingOverdueEvent("2026/1", "2026/12", "Clean Architecture", limitDate, 5, 1000);

        assertEquals("2026/1", event.lendingNumber());
        assertEquals("2026/12", event.readerNumber());
        assertEquals("Clean Architecture", event.bookTitle());
        assertEquals(limitDate, event.limitDate());
        assertEquals(5, event.daysOverdue());
        assertEquals(1000, event.fineValueInCents());
    }

    @Test
    void testLendingOverdueEventValidation() {
        LocalDate limitDate = LocalDate.now().minusDays(5);
        assertThrows(NullPointerException.class, () -> new LendingOverdueEvent(null, "2026/12", "CA", limitDate, 5, 1000));
        assertThrows(NullPointerException.class, () -> new LendingOverdueEvent("2026/1", null, "CA", limitDate, 5, 1000));
        assertThrows(NullPointerException.class, () -> new LendingOverdueEvent("2026/1", "2026/12", null, limitDate, 5, 1000));
        assertThrows(NullPointerException.class, () -> new LendingOverdueEvent("2026/1", "2026/12", "CA", null, 5, 1000));
        assertThrows(IllegalArgumentException.class, () -> new LendingOverdueEvent("2026/1", "2026/12", "CA", limitDate, -1, 1000));
        assertThrows(IllegalArgumentException.class, () -> new LendingOverdueEvent("2026/1", "2026/12", "CA", limitDate, 5, -10));
    }
}
