package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;

import java.util.List;

/**
 * Strategy interface for lending policies.
 * Encapsulates lending duration, simultaneous lending limits, fine calculations,
 * and eligibility validation.
 *
 * Supports runtime parameter updates and runtime dynamic policy creation.
 */
public interface LendingPolicy {

    /**
     * Unique identifier/name of the lending policy.
     */
    String getPolicyName();

    /**
     * Checks if this policy is applicable to the given reader and book.
     */
    boolean isApplicable(ReaderDetails reader, Book book);

    /**
     * Checks if this policy is applicable considering the reader's current active lendings.
     * Default implementation delegates to isApplicable(reader, book).
     */
    default boolean isApplicable(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        return isApplicable(reader, book);
    }

    /**
     * Validates whether the lending can proceed under this policy's rules.
     *
     * @param reader reader requesting the lending
     * @param book book to be lent
     * @param activeLendings currently active (non-returned) lendings of the reader
     */
    void validateCanLend(ReaderDetails reader, Book book, List<Lending> activeLendings);

    /**
     * Maximum lending duration allowed in days.
     */
    int getLendingDurationInDays();

    /**
     * Updates the lending duration in days in runtime.
     */
    void setLendingDurationInDays(int days);

    /**
     * Maximum number of simultaneous lendings allowed for a reader under this policy.
     */
    int getMaxSimultaneousLendings();

    /**
     * Updates the maximum simultaneous lendings in runtime.
     */
    void setMaxSimultaneousLendings(int max);

    /**
     * Fine value per overdue day in cents.
     */
    int getFineValuePerDayInCents();

    /**
     * Updates the fine value per day in cents in runtime.
     */
    void setFineValuePerDayInCents(int fine);

    /**
     * Priority of the policy when multiple policies are applicable.
     * Higher numeric value means higher precedence.
     */
    int getPriority();

    /**
     * Updates policy priority in runtime.
     */
    void setPriority(int priority);

    /**
     * Human-readable description of the policy.
     */
    default String getDescription() {
        return getPolicyName();
    }
}
