package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.configuration.LendingPolicyProperties;
import pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;

import java.time.LocalDate;
import java.util.List;

/**
 * Adaptive Lending Policy (Lending Policies Specification v1.0, Section 3 & Section 4.2).
 *
 * <p>Adapts the lending period according to the number of active lendings of the reader:
 * <ul>
 *   <li>3.1. A reader may have at most {@code MaxActiveLendings (> 0)} active lendings.</li>
 *   <li>3.2. When the reader currently has fewer than {@code FirstThreshold} active lendings,
 *            the lending period is {@code LongPeriod (> 0)} days.</li>
 *   <li>3.3. When the reader currently has at least {@code FirstThreshold} and fewer than
 *            {@code SecondThreshold} active lendings, the lending period is {@code StandardPeriod (> 0)} days.</li>
 *   <li>3.4. When the reader currently has {@code SecondThreshold} or more active lendings,
 *            the lending period is {@code ShortPeriod (> 0)} days.</li>
 *   <li>3.5. Parameter constraints:
 *       <ul>
 *         <li>{@code 0 < FirstThreshold < SecondThreshold < MaxActiveLendings}</li>
 *         <li>{@code LongPeriod > StandardPeriod > ShortPeriod > 0}</li>
 *       </ul>
 *   </li>
 *   <li>3.6. A lending may be renewed only if the resulting lending continues to satisfy the applicable conditions of this policy.</li>
 *   <li>3.7. A reader with any overdue lending cannot start a new lending.</li>
 * </ul>
 */
@Component
public class AdaptiveLendingPolicy implements LendingPolicy {

    public static final String POLICY_NAME = "ADAPTIVE";

    private int maxActiveLendings;
    private int firstThreshold;
    private int secondThreshold;
    private int longPeriod;
    private int standardPeriod;
    private int shortPeriod;
    private int maxRenewals;
    private int renewalPeriod;
    private int fineValuePerDayInCents;
    private int priority;

    private int historyPeriodMonths;
    private int minCompletedLendings;
    private double minOnTimeRatio;

    @Autowired
    public AdaptiveLendingPolicy(LendingPolicyProperties properties) {
        if (properties != null && properties.getAdaptive() != null) {
            properties.getAdaptive().validateConstraints();
            LendingPolicyProperties.AdaptiveParams a = properties.getAdaptive();
            this.maxActiveLendings = a.getMaxActiveLendings();
            this.firstThreshold = a.getFirstThreshold();
            this.secondThreshold = a.getSecondThreshold();
            this.longPeriod = a.getLongPeriod();
            this.standardPeriod = a.getStandardPeriod();
            this.shortPeriod = a.getShortPeriod();
            this.maxRenewals = a.getMaxRenewals();
            this.renewalPeriod = a.getRenewalPeriod();
            this.fineValuePerDayInCents = a.getFineValuePerDayInCents();
        } else {
            this.maxActiveLendings = 5;
            this.firstThreshold = 1;
            this.secondThreshold = 2;
            this.longPeriod = 30;
            this.standardPeriod = 15;
            this.shortPeriod = 7;
            this.maxRenewals = 2;
            this.renewalPeriod = 15;
            this.fineValuePerDayInCents = 200;
        }

        if (properties != null && properties.getSelection() != null) {
            properties.getSelection().validateConstraints();
            this.historyPeriodMonths = properties.getSelection().getHistoryPeriod();
            this.minCompletedLendings = properties.getSelection().getMinCompletedLendings();
            this.minOnTimeRatio = properties.getSelection().getMinOnTimeRatio();
        } else {
            this.historyPeriodMonths = 6;
            this.minCompletedLendings = 3;
            this.minOnTimeRatio = 80.0;
        }

        validateConstraints(this.maxActiveLendings, this.firstThreshold, this.secondThreshold,
                this.longPeriod, this.standardPeriod, this.shortPeriod, this.maxRenewals, this.renewalPeriod);
        this.priority = 25;
    }

    public AdaptiveLendingPolicy(int maxActiveLendings,
                                 int firstThreshold,
                                 int secondThreshold,
                                 int longPeriod,
                                 int standardPeriod,
                                 int shortPeriod,
                                 int maxRenewals,
                                 int renewalPeriod) {
        validateConstraints(maxActiveLendings, firstThreshold, secondThreshold,
                longPeriod, standardPeriod, shortPeriod, maxRenewals, renewalPeriod);
        this.maxActiveLendings = maxActiveLendings;
        this.firstThreshold = firstThreshold;
        this.secondThreshold = secondThreshold;
        this.longPeriod = longPeriod;
        this.standardPeriod = standardPeriod;
        this.shortPeriod = shortPeriod;
        this.maxRenewals = maxRenewals;
        this.renewalPeriod = renewalPeriod;
        this.fineValuePerDayInCents = 200;
        this.historyPeriodMonths = 6;
        this.minCompletedLendings = 3;
        this.minOnTimeRatio = 80.0;
        this.priority = 25;
    }

    public AdaptiveLendingPolicy(int maxActiveLendings,
                                 int firstThreshold,
                                 int secondThreshold,
                                 int longPeriod,
                                 int standardPeriod,
                                 int shortPeriod) {
        this(maxActiveLendings, firstThreshold, secondThreshold, longPeriod, standardPeriod, shortPeriod, 2, standardPeriod);
    }

    public AdaptiveLendingPolicy() {
        this(null);
    }

    /**
     * Validates Section 3.5 constraints:
     * 0 < FirstThreshold < SecondThreshold < MaxActiveLendings
     * LongPeriod > StandardPeriod > ShortPeriod > 0
     */
    public static void validateConstraints(int maxActiveLendings,
                                           int firstThreshold,
                                           int secondThreshold,
                                           int longPeriod,
                                           int standardPeriod,
                                           int shortPeriod,
                                           int maxRenewals,
                                           int renewalPeriod) {
        if (firstThreshold <= 0) {
            throw new IllegalArgumentException("FirstThreshold must be greater than 0");
        }
        if (secondThreshold <= firstThreshold) {
            throw new IllegalArgumentException("SecondThreshold must be greater than FirstThreshold");
        }
        if (maxActiveLendings <= secondThreshold) {
            throw new IllegalArgumentException("MaxActiveLendings must be greater than SecondThreshold");
        }
        if (shortPeriod <= 0) {
            throw new IllegalArgumentException("ShortPeriod must be greater than 0");
        }
        if (standardPeriod <= shortPeriod) {
            throw new IllegalArgumentException("StandardPeriod must be greater than ShortPeriod");
        }
        if (longPeriod <= standardPeriod) {
            throw new IllegalArgumentException("LongPeriod must be greater than StandardPeriod");
        }
        if (maxRenewals < 0) {
            throw new IllegalArgumentException("MaxRenewals must be greater than or equal to 0");
        }
        if (renewalPeriod <= 0) {
            throw new IllegalArgumentException("RenewalPeriod must be greater than 0");
        }
    }

    @Override
    public String getPolicyName() {
        return POLICY_NAME;
    }

    @Override
    public boolean isApplicable(ReaderDetails reader, Book book) {
        return false;
    }

    @Override
    public boolean isApplicable(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        return false;
    }

    /**
     * Evaluates Section 4.2 selection criteria:
     * Adaptive Lending Policy applies when, during the previous HistoryPeriod (> 0) months, the reader:
     * 4.2.1. has completed at least MinCompletedLendings (> 0) lendings;
     * 4.2.2. has returned at least MinOnTimeRatio (0-100%) of those lendings on time; and
     * 4.2.3. has no currently overdue lending.
     */
    @Override
    public boolean isApplicable(ReaderDetails reader, Book book, List<Lending> activeLendings, List<Lending> readerHistory) {
        // 4.2.3. Reader has no currently overdue lending
        if (activeLendings != null) {
            for (Lending active : activeLendings) {
                if (active.getDaysDelayed() > 0) {
                    return false;
                }
            }
        }
        if (readerHistory == null || readerHistory.isEmpty()) {
            return false;
        }

        LocalDate cutoffDate = LocalDate.now().minusMonths(historyPeriodMonths);
        int completedCount = 0;
        int onTimeCount = 0;

        for (Lending l : readerHistory) {
            if (l.getReturnedDate() == null) {
                // If an unreturned lending in history is overdue, also block
                if (l.getDaysDelayed() > 0) {
                    return false;
                }
                continue;
            }
            // Check if completed within the previous HistoryPeriod months
            LocalDate eventDate = l.getStartDate() != null ? l.getStartDate() : l.getReturnedDate();
            if (!eventDate.isBefore(cutoffDate) || !l.getReturnedDate().isBefore(cutoffDate)) {
                completedCount++;
                if (l.getDaysDelayed() == 0) {
                    onTimeCount++;
                }
            }
        }

        // 4.2.1. Completed at least MinCompletedLendings
        if (completedCount < minCompletedLendings) {
            return false;
        }

        // 4.2.2. Returned at least MinOnTimeRatio (0-100%) on time
        double actualRatio = (onTimeCount * 100.0) / completedCount;
        return actualRatio >= minOnTimeRatio;
    }

    /**
     * Computes the lending period in days based on the number of active lendings (Sections 3.2, 3.3, 3.4):
     * - activeCount < FirstThreshold -> LongPeriod
     * - FirstThreshold <= activeCount < SecondThreshold -> StandardPeriod
     * - activeCount >= SecondThreshold -> ShortPeriod
     */
    public int computeLendingPeriod(int activeLendingsCount) {
        if (activeLendingsCount < 0) {
            throw new IllegalArgumentException("Active lendings count cannot be negative");
        }
        if (activeLendingsCount >= maxActiveLendings) {
            throw new LendingForbiddenException(
                    "Reader has reached the maximum number of simultaneous lendings (" + maxActiveLendings + ")");
        }
        if (activeLendingsCount < firstThreshold) {
            return longPeriod;
        }
        if (activeLendingsCount < secondThreshold) {
            return standardPeriod;
        }
        return shortPeriod;
    }

    @Override
    public int getLendingDurationInDays(List<Lending> activeLendings) {
        int activeCount = activeLendings != null ? activeLendings.size() : 0;
        if (activeCount < firstThreshold) {
            return longPeriod;
        }
        if (activeCount < secondThreshold) {
            return standardPeriod;
        }
        return shortPeriod;
    }

    @Override
    public int getLendingDurationInDays() {
        return longPeriod;
    }

    @Override
    public void setLendingDurationInDays(int days) {
        validateConstraints(this.maxActiveLendings, this.firstThreshold, this.secondThreshold,
                days, this.standardPeriod, this.shortPeriod, this.maxRenewals, this.renewalPeriod);
        this.longPeriod = days;
    }

    @Override
    public void validateCanLend(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        if (activeLendings == null) {
            return;
        }

        // 3.7. A reader with any overdue lending cannot start a new lending.
        for (Lending l : activeLendings) {
            if (l.getDaysDelayed() > 0) {
                throw new LendingForbiddenException("Reader has book(s) past their due date");
            }
        }

        // 3.1. A reader may have at most MaxActiveLendings (> 0) active lendings.
        if (activeLendings.size() >= maxActiveLendings) {
            throw new LendingForbiddenException(
                    "Reader has reached the maximum number of simultaneous lendings (" + maxActiveLendings + ")");
        }
    }

    /**
     * 3.6. A lending may be renewed only if the resulting lending continues to satisfy
     * the applicable conditions of this policy.
     */
    @Override
    public void validateCanRenew(Lending lending, ReaderDetails reader, List<Lending> activeLendings) {
        if (lending == null) {
            throw new IllegalArgumentException("Lending cannot be null");
        }
        if (lending.getReturnedDate() != null) {
            throw new LendingForbiddenException("Cannot renew a returned lending");
        }
        if (lending.getDaysDelayed() > 0) {
            throw new LendingForbiddenException("An overdue lending cannot be renewed");
        }
        if (activeLendings != null) {
            for (Lending active : activeLendings) {
                if (active.getDaysDelayed() > 0) {
                    throw new LendingForbiddenException("Reader has overdue lendings");
                }
            }
            if (activeLendings.size() > maxActiveLendings) {
                throw new LendingForbiddenException(
                        "Reader exceeds maximum active lendings (" + maxActiveLendings + ")");
            }
        }
        if (lending.getRenewalCount() >= maxRenewals) {
            throw new LendingForbiddenException(
                    "Lending has reached the maximum number of renewals (" + maxRenewals + ")");
        }
    }

    @Override
    public int getRenewalPeriodInDays(List<Lending> activeLendings) {
        // Under Adaptive policy, renewal extends according to the applicable period for the active lendings
        // (excluding the lending being renewed so 1 active lending stays < FirstThreshold if FirstThreshold == 1,
        // or using configured renewalPeriod if explicitly set).
        int otherActiveCount = (activeLendings != null && !activeLendings.isEmpty())
                ? activeLendings.size() - 1
                : 0;
        if (otherActiveCount < firstThreshold) {
            return longPeriod;
        }
        if (otherActiveCount < secondThreshold) {
            return standardPeriod;
        }
        return shortPeriod;
    }

    @Override
    public int getRenewalPeriodInDays() {
        return renewalPeriod;
    }

    @Override
    public void setRenewalPeriodInDays(int renewalPeriod) {
        if (renewalPeriod <= 0) {
            throw new IllegalArgumentException("RenewalPeriod must be greater than 0");
        }
        this.renewalPeriod = renewalPeriod;
    }

    @Override
    public int getMaxSimultaneousLendings() {
        return maxActiveLendings;
    }

    public int getMaxActiveLendings() {
        return maxActiveLendings;
    }

    @Override
    public void setMaxSimultaneousLendings(int max) {
        validateConstraints(max, this.firstThreshold, this.secondThreshold,
                this.longPeriod, this.standardPeriod, this.shortPeriod, this.maxRenewals, this.renewalPeriod);
        this.maxActiveLendings = max;
    }

    public int getFirstThreshold() {
        return firstThreshold;
    }

    public void setFirstThreshold(int firstThreshold) {
        validateConstraints(this.maxActiveLendings, firstThreshold, this.secondThreshold,
                this.longPeriod, this.standardPeriod, this.shortPeriod, this.maxRenewals, this.renewalPeriod);
        this.firstThreshold = firstThreshold;
    }

    public int getSecondThreshold() {
        return secondThreshold;
    }

    public void setSecondThreshold(int secondThreshold) {
        validateConstraints(this.maxActiveLendings, this.firstThreshold, secondThreshold,
                this.longPeriod, this.standardPeriod, this.shortPeriod, this.maxRenewals, this.renewalPeriod);
        this.secondThreshold = secondThreshold;
    }

    public int getLongPeriod() {
        return longPeriod;
    }

    public void setLongPeriod(int longPeriod) {
        validateConstraints(this.maxActiveLendings, this.firstThreshold, this.secondThreshold,
                longPeriod, this.standardPeriod, this.shortPeriod, this.maxRenewals, this.renewalPeriod);
        this.longPeriod = longPeriod;
    }

    public int getStandardPeriod() {
        return standardPeriod;
    }

    public void setStandardPeriod(int standardPeriod) {
        validateConstraints(this.maxActiveLendings, this.firstThreshold, this.secondThreshold,
                this.longPeriod, standardPeriod, this.shortPeriod, this.maxRenewals, this.renewalPeriod);
        this.standardPeriod = standardPeriod;
    }

    public int getShortPeriod() {
        return shortPeriod;
    }

    public void setShortPeriod(int shortPeriod) {
        validateConstraints(this.maxActiveLendings, this.firstThreshold, this.secondThreshold,
                this.longPeriod, this.standardPeriod, shortPeriod, this.maxRenewals, this.renewalPeriod);
        this.shortPeriod = shortPeriod;
    }

    @Override
    public int getMaxRenewals() {
        return maxRenewals;
    }

    @Override
    public void setMaxRenewals(int maxRenewals) {
        if (maxRenewals < 0) {
            throw new IllegalArgumentException("MaxRenewals must be greater than or equal to 0");
        }
        this.maxRenewals = maxRenewals;
    }

    @Override
    public int getFineValuePerDayInCents() {
        return fineValuePerDayInCents;
    }

    @Override
    public void setFineValuePerDayInCents(int fine) {
        this.fineValuePerDayInCents = fine;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getHistoryPeriodMonths() {
        return historyPeriodMonths;
    }

    public void setHistoryPeriodMonths(int historyPeriodMonths) {
        if (historyPeriodMonths <= 0) {
            throw new IllegalArgumentException("HistoryPeriod must be greater than 0");
        }
        this.historyPeriodMonths = historyPeriodMonths;
    }

    public int getMinCompletedLendings() {
        return minCompletedLendings;
    }

    public void setMinCompletedLendings(int minCompletedLendings) {
        if (minCompletedLendings <= 0) {
            throw new IllegalArgumentException("MinCompletedLendings must be greater than 0");
        }
        this.minCompletedLendings = minCompletedLendings;
    }

    public double getMinOnTimeRatio() {
        return minOnTimeRatio;
    }

    public void setMinOnTimeRatio(double minOnTimeRatio) {
        if (minOnTimeRatio < 0.0 || minOnTimeRatio > 100.0) {
            throw new IllegalArgumentException("MinOnTimeRatio must be between 0 and 100");
        }
        this.minOnTimeRatio = minOnTimeRatio;
    }

    @Override
    public String getDescription() {
        return "Adaptive lending policy (Long=" + longPeriod + "d [<" + firstThreshold + "], Standard="
                + standardPeriod + "d [" + firstThreshold + ".." + (secondThreshold - 1) + "], Short="
                + shortPeriod + "d [>=" + secondThreshold + "], max=" + maxActiveLendings + ")";
    }
}
