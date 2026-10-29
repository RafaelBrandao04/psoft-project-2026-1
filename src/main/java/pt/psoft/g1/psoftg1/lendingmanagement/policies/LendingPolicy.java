package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;

import java.util.List;

/**
 * Strategy / Policy Port for Book Lending Rules (Lending Policies Specification v1.0).
 *
 * <p>Allows supporting multiple lending policies (Standard, Adaptive, and dynamic/custom policies),
 * configuring quantitative parameters externally at setup time, and modifying or adding policies
 * without modifying the core {@code LendingServiceImpl}.</p>
 */
public interface LendingPolicy {

    /**
     * Unique name identifier of the lending policy (e.g., "STANDARD", "ADAPTIVE").
     */
    String getPolicyName();

    /**
     * Evaluates whether this lending policy applies to the given reader and book context.
     *
     * @param reader the reader requesting the lending
     * @param book   the book being borrowed
     * @return true if this policy is applicable, false otherwise
     */
    boolean isApplicable(ReaderDetails reader, Book book);

    /**
     * Evaluates whether this lending policy applies given the reader, book, and current active lendings.
     *
     * @param reader         the reader requesting the lending
     * @param book           the book being borrowed
     * @param activeLendings the reader's currently unreturned lendings
     * @return true if this policy is applicable, false otherwise
     */
    default boolean isApplicable(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        return isApplicable(reader, book);
    }

    /**
     * Evaluates whether this lending policy applies given the reader, book, active lendings,
     * and the reader's lending history (Section 4 of Lending Policies Specification v1.0).
     *
     * @param reader         the reader requesting the lending
     * @param book           the book being borrowed
     * @param activeLendings the reader's currently unreturned lendings
     * @param readerHistory  the reader's historical lendings
     * @return true if this policy is applicable, false otherwise
     */
    default boolean isApplicable(ReaderDetails reader, Book book, List<Lending> activeLendings, List<Lending> readerHistory) {
        return isApplicable(reader, book, activeLendings);
    }

    /**
     * Validates whether the reader is allowed to borrow the book under this policy,
     * given their current list of outstanding (unreturned) lendings.
     *
     * @param reader         the reader requesting the lending
     * @param book           the book being borrowed
     * @param activeLendings list of currently outstanding lendings for this reader
     * @throws pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException if the lending violates policy rules
     */
    void validateCanLend(ReaderDetails reader, Book book, List<Lending> activeLendings);

    /**
     * Validates whether the given lending may be renewed under this policy.
     *
     * @param lending        the lending being renewed
     * @param reader         the reader who owns the lending
     * @param activeLendings list of currently active lendings for this reader
     * @throws pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException if renewal violates policy rules
     */
    default void validateCanRenew(Lending lending, ReaderDetails reader, List<Lending> activeLendings) {
        if (lending == null) {
            throw new IllegalArgumentException("Lending cannot be null");
        }
        if (lending.getReturnedDate() != null) {
            throw new pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException("Cannot renew a returned lending");
        }
        if (lending.getDaysDelayed() > 0) {
            throw new pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException("An overdue lending cannot be renewed");
        }
        if (activeLendings != null) {
            for (Lending active : activeLendings) {
                if (active.getDaysDelayed() > 0) {
                    throw new pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException("Reader has overdue lendings");
                }
            }
            if (activeLendings.size() > getMaxSimultaneousLendings()) {
                throw new pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException(
                        "Reader exceeds maximum active lendings (" + getMaxSimultaneousLendings() + ")");
            }
        }
        if (lending.getRenewalCount() >= getMaxRenewals()) {
            throw new pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException(
                    "Lending has reached the maximum number of renewals (" + getMaxRenewals() + ")");
        }
    }

    /**
     * Returns the base lending duration in days granted by this policy.
     */
    int getLendingDurationInDays();

    /**
     * Computes the lending duration in days for a new lending given the reader's current active lendings.
     * For Standard policy this is fixed; for Adaptive policy this adapts to the number of active lendings.
     */
    default int getLendingDurationInDays(List<Lending> activeLendings) {
        return getLendingDurationInDays();
    }

    /**
     * Updates the lending duration in days at runtime (admin management).
     */
    default void setLendingDurationInDays(int days) {
        // optional for immutable policies
    }

    /**
     * Returns the maximum number of simultaneous active lendings allowed by this policy (MaxActiveLendings).
     */
    int getMaxSimultaneousLendings();

    /**
     * Updates the maximum number of simultaneous active lendings at runtime.
     */
    default void setMaxSimultaneousLendings(int max) {
        // optional
    }

    /**
     * Returns the maximum number of renewals allowed for a lending under this policy (MaxRenewals).
     */
    default int getMaxRenewals() {
        return 2;
    }

    default void setMaxRenewals(int maxRenewals) {
        // optional
    }

    /**
     * Returns the renewal period in days (RenewalPeriod).
     */
    default int getRenewalPeriodInDays() {
        return getLendingDurationInDays();
    }

    /**
     * Returns the renewal period in days given the reader's current active lendings.
     */
    default int getRenewalPeriodInDays(List<Lending> activeLendings) {
        return getRenewalPeriodInDays();
    }

    default void setRenewalPeriodInDays(int renewalPeriodInDays) {
        // optional
    }

    /**
     * Returns the daily fine in cents applied when a lending is returned late under this policy.
     */
    int getFineValuePerDayInCents();

    /**
     * Updates the daily fine in cents at runtime.
     */
    default void setFineValuePerDayInCents(int fine) {
        // optional
    }

    /**
     * Priority order when multiple policies are applicable (higher number = higher priority).
     */
    int getPriority();

    /**
     * Updates the priority order at runtime.
     */
    default void setPriority(int priority) {
        // optional
    }

    /**
     * Description of the policy criteria for admin inspection.
     */
    default String getDescription() {
        return getPolicyName() + " policy (" + getLendingDurationInDays() + " days, max " + getMaxSimultaneousLendings() + " books)";
    }
}
