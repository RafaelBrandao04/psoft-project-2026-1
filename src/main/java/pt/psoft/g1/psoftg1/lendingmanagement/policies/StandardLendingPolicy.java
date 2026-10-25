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
 * Standard Lending Policy.
 * Serves as the baseline fallback policy and applies when a reader is borrowing their 3rd book
 * (i.e. already has 2 active lendings).
 *
 * Parameters can be configured at boot time via properties and modified in runtime by LMS admins.
 */
@Component
public class StandardLendingPolicy implements LendingPolicy {

    public static final String POLICY_NAME = "STANDARD";

    private int durationInDays;
    private int maxSimultaneousLendings;
    private int fineValuePerDayInCents;
    private int priority;

    @Autowired
    public StandardLendingPolicy(LendingPolicyProperties properties) {
        if (properties != null && properties.getStandard() != null) {
            this.durationInDays = properties.getStandard().getDurationInDays();
            this.maxSimultaneousLendings = properties.getStandard().getMaxSimultaneousLendings();
            this.fineValuePerDayInCents = properties.getStandard().getFineValuePerDayInCents();
        } else {
            this.durationInDays = 15;
            this.maxSimultaneousLendings = 5;
            this.fineValuePerDayInCents = 200;
        }
        this.priority = 10;
    }

    public StandardLendingPolicy() {
        this(null);
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
        // Standard policy applies when the reader has 2 active lendings (taking book #3),
        // or as universal baseline fallback.
        return true;
    }

    @Override
    public void validateCanLend(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        if (activeLendings == null) {
            return;
        }

        // Check overdue items
        for (Lending l : activeLendings) {
            if (l.getDaysDelayed() > 0) {
                throw new LendingForbiddenException("Reader has overdue lendings");
            }
        }

        // Check simultaneous limit
        if (activeLendings.size() >= maxSimultaneousLendings) {
            throw new LendingForbiddenException(
                    "Reader has reached the maximum number of simultaneous lendings (" + maxSimultaneousLendings + ")");
        }
    }

    @Override
    public int getLendingDurationInDays() {
        return durationInDays;
    }

    @Override
    public void setLendingDurationInDays(int days) {
        this.durationInDays = days;
    }

    @Override
    public int getMaxSimultaneousLendings() {
        return maxSimultaneousLendings;
    }

    @Override
    public void setMaxSimultaneousLendings(int max) {
        this.maxSimultaneousLendings = max;
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
        return "Standard policy (baseline / 3rd book: " + durationInDays + " days)";
    }
}
