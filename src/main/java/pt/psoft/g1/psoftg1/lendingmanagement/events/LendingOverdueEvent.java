package pt.psoft.g1.psoftg1.lendingmanagement.events;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Domain event published when an outstanding lending is overdue.
 * Carries information to notify readers about overdue books and accumulated fine.
 */
public record LendingOverdueEvent(
        String lendingNumber,
        String readerNumber,
        String bookTitle,
        LocalDate limitDate,
        int daysOverdue,
        int fineValueInCents
) {
    public LendingOverdueEvent {
        Objects.requireNonNull(lendingNumber, "lendingNumber must not be null");
        Objects.requireNonNull(readerNumber, "readerNumber must not be null");
        Objects.requireNonNull(bookTitle, "bookTitle must not be null");
        Objects.requireNonNull(limitDate, "limitDate must not be null");
        if (daysOverdue < 0) {
            throw new IllegalArgumentException("daysOverdue cannot be negative");
        }
        if (fineValueInCents < 0) {
            throw new IllegalArgumentException("fineValueInCents cannot be negative");
        }
    }
}
