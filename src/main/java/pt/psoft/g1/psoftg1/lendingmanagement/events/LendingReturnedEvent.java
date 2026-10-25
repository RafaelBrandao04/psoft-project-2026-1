package pt.psoft.g1.psoftg1.lendingmanagement.events;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Domain event published when a lending is marked as returned.
 * Carries information about return date, delay, and any applied fine.
 */
public record LendingReturnedEvent(
        String lendingNumber,
        String readerNumber,
        String bookTitle,
        LocalDate returnedDate,
        int daysDelayed,
        Integer fineValueInCents
) {
    public LendingReturnedEvent {
        Objects.requireNonNull(lendingNumber, "lendingNumber must not be null");
        Objects.requireNonNull(readerNumber, "readerNumber must not be null");
        Objects.requireNonNull(bookTitle, "bookTitle must not be null");
        Objects.requireNonNull(returnedDate, "returnedDate must not be null");
    }

    public boolean hasFine() {
        return fineValueInCents != null && fineValueInCents > 0;
    }
}
