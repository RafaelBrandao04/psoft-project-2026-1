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
 * Long-Term Lending Policy.
 * Applies when a reader currently has fewer than 2 active lendings (i.e. borrowing book #1 or book #2).
 * Grants a longer return period (e.g. 30 days).
 *
 * Parameters can be configured at boot time via properties and modified in runtime by LMS admins.
 */
@Component
public class LongTermLendingPolicy implements LendingPolicy {

    public static final String POLICY_NAME = "LONG_TERM";

    private int durationInDays;
    private int maxSimultaneousLendings;
    private int fineValuePerDayInCents;
    private int priority;

    @Autowired
    public LongTermLendingPolicy(LendingPolicyProperties properties) {
        if (properties != null && properties.getLongTerm() != null) {
            this.durationInDays = properties.getLongTerm().getDurationInDays();
            this.maxSimultaneousLendings = properties.getLongTerm().getMaxSimultaneousLendings();
            this.fineValuePerDayInCents = properties.getLongTerm().getFineValuePerDayInCents();
        } else {
            this.durationInDays = 30;
            this.maxSimultaneousLendings = 5;
            this.fineValuePerDayInCents = 100;
        }
        this.priority = 15;
    }

    public LongTermLendingPolicy() {
        this(null);
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
        // Applies if reader has 0 or 1 active lendings (borrowing 1st or 2nd book)
        return activeLendings == null || activeLendings.size() < 2;
    }

    @Override
    public void validateCanLend(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        if (activeLendings == null) {
            return;
        }

        for (Lending l : activeLendings) {
            if (l.getDaysDelayed() > 0) {
                throw new LendingForbiddenException("Reader has overdue lendings");
            }
        }

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
        return "Long-term policy (1st and 2nd books: " + durationInDays + " days)";
    }
}

