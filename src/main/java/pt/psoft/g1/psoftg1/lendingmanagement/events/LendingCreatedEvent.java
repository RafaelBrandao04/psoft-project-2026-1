package pt.psoft.g1.psoftg1.lendingmanagement.events;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Domain event published when a new lending is successfully created.
 * Encapsulates the essential business data needed for downstream listeners,
 * completely decoupled from specific delivery mechanisms.
 */
public record LendingCreatedEvent(
        String lendingNumber,
        String readerNumber,
        String bookTitle,
        String isbn,
        LocalDate limitDate
) {
    public LendingCreatedEvent {
        Objects.requireNonNull(lendingNumber, "lendingNumber must not be null");
        Objects.requireNonNull(readerNumber, "readerNumber must not be null");
        Objects.requireNonNull(bookTitle, "bookTitle must not be null");
        Objects.requireNonNull(isbn, "isbn must not be null");
        Objects.requireNonNull(limitDate, "limitDate must not be null");
    }
}
