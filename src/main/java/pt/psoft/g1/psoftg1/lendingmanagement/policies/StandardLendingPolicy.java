package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.configuration.LendingPolicyProperties;
import pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;

import java.util.List;

/**
 * Standard Lending Policy (Lending Policies Specification v1.0, Section 2).
 *
 * <p>Rules:
 * <ul>
 *   <li>2.1. A reader may have at most {@code MaxActiveLendings (> 0)} active lendings.</li>
 *   <li>2.2. The lending period is {@code LendingPeriod (> 0)} days.</li>
 *   <li>2.3. A lending may be renewed at most {@code MaxRenewals (>= 0)} times, each time for {@code RenewalPeriod (> 0)} days.</li>
 *   <li>2.4. A reader with any overdue lending cannot start a new lending.</li>
 *   <li>2.5. An overdue lending cannot be renewed.</li>
 * </ul>
 */
@Component
public class StandardLendingPolicy implements LendingPolicy {

    public static final String POLICY_NAME = "STANDARD";

    private int durationInDays;
    private int maxSimultaneousLendings;
    private int maxRenewals;
    private int renewalPeriodInDays;
    private int fineValuePerDayInCents;
    private int priority;

    @Autowired
    public StandardLendingPolicy(LendingPolicyProperties properties) {
        if (properties != null && properties.getStandard() != null) {
            properties.getStandard().validateConstraints();
            this.durationInDays = properties.getStandard().getDurationInDays();
            this.maxSimultaneousLendings = properties.getStandard().getMaxSimultaneousLendings();
            this.maxRenewals = properties.getStandard().getMaxRenewals();
            this.renewalPeriodInDays = properties.getStandard().getRenewalPeriodInDays();
            this.fineValuePerDayInCents = properties.getStandard().getFineValuePerDayInCents();
        } else {
            this.durationInDays = 15;
            this.maxSimultaneousLendings = 3;
            this.maxRenewals = 2;
            this.renewalPeriodInDays = 15;
            this.fineValuePerDayInCents = 200;
        }
        validateConstraints(this.maxSimultaneousLendings, this.durationInDays, this.maxRenewals, this.renewalPeriodInDays);
        this.priority = 10;
    }

    public StandardLendingPolicy(int maxActiveLendings, int lendingPeriod, int maxRenewals, int renewalPeriod) {
        validateConstraints(maxActiveLendings, lendingPeriod, maxRenewals, renewalPeriod);
        this.maxSimultaneousLendings = maxActiveLendings;
        this.durationInDays = lendingPeriod;
        this.maxRenewals = maxRenewals;
        this.renewalPeriodInDays = renewalPeriod;
        this.fineValuePerDayInCents = 200;
        this.priority = 10;
    }

    public StandardLendingPolicy() {
        this(null);
    }

    private static void validateConstraints(int maxActiveLendings, int lendingPeriod, int maxRenewals, int renewalPeriod) {
        if (maxActiveLendings <= 0) {
            throw new IllegalArgumentException("MaxActiveLendings must be greater than 0");
        }
        if (lendingPeriod <= 0) {
            throw new IllegalArgumentException("LendingPeriod must be greater than 0");
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
        return true;
    }

    @Override
    public boolean isApplicable(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        return true;
    }

    @Override
    public boolean isApplicable(ReaderDetails reader, Book book, List<Lending> activeLendings, List<Lending> readerHistory) {
        return true;
    }

    @Override
    public void validateCanLend(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        if (activeLendings == null) {
            return;
        }

        // 2.4. A reader with any overdue lending cannot start a new lending.
        for (Lending l : activeLendings) {
            if (l.getDaysDelayed() > 0) {
                throw new LendingForbiddenException("Reader has book(s) past their due date");
            }
        }

        // 2.1. A reader may have at most MaxActiveLendings (> 0) active lendings.
        if (activeLendings.size() >= maxSimultaneousLendings) {
            throw new LendingForbiddenException(
                    "Reader has reached the maximum number of simultaneous lendings (" + maxSimultaneousLendings + ")");
        }
    }

    @Override
    public void validateCanRenew(Lending lending, ReaderDetails reader, List<Lending> activeLendings) {
        if (lending == null) {
            throw new IllegalArgumentException("Lending cannot be null");
        }
        if (lending.getReturnedDate() != null) {
            throw new LendingForbiddenException("Cannot renew a returned lending");
        }
        // 2.5. An overdue lending cannot be renewed.
        if (lending.getDaysDelayed() > 0) {
            throw new LendingForbiddenException("An overdue lending cannot be renewed");
        }
        if (activeLendings != null) {
            for (Lending active : activeLendings) {
                if (active.getDaysDelayed() > 0) {
                    throw new LendingForbiddenException("Reader has overdue lendings");
                }
            }
            if (activeLendings.size() > maxSimultaneousLendings) {
                throw new LendingForbiddenException(
                        "Reader exceeds maximum active lendings (" + maxSimultaneousLendings + ")");
            }
        }
        // 2.3. A lending may be renewed at most MaxRenewals (>= 0) times.
        if (lending.getRenewalCount() >= maxRenewals) {
            throw new LendingForbiddenException(
                    "Lending has reached the maximum number of renewals (" + maxRenewals + ")");
        }
    }

    @Override
    public int getLendingDurationInDays() {
        return durationInDays;
    }

    @Override
    public void setLendingDurationInDays(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("LendingPeriod must be greater than 0");
        }
        this.durationInDays = days;
    }

    @Override
    public int getMaxSimultaneousLendings() {
        return maxSimultaneousLendings;
    }

    public int getMaxActiveLendings() {
        return maxSimultaneousLendings;
    }

    @Override
    public void setMaxSimultaneousLendings(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("MaxActiveLendings must be greater than 0");
        }
        this.maxSimultaneousLendings = max;
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
    public int getRenewalPeriodInDays() {
        return renewalPeriodInDays;
    }

    @Override
    public void setRenewalPeriodInDays(int renewalPeriodInDays) {
        if (renewalPeriodInDays <= 0) {
            throw new IllegalArgumentException("RenewalPeriod must be greater than 0");
        }
        this.renewalPeriodInDays = renewalPeriodInDays;
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

    @Override
    public String getDescription() {
        return "Standard lending policy (period: " + durationInDays + " days, max active: " + maxSimultaneousLendings
                + ", max renewals: " + maxRenewals + ")";
    }
}
