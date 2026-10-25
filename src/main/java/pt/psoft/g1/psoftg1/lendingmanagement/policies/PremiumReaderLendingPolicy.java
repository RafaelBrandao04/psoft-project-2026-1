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
 * Premium Reader Lending Policy.
 * Applicable to special reader categories (e.g. researchers, faculty, premium members).
 * Grants extended lending periods (30 days) and up to 5 simultaneous lendings.
 *
 * Parameters can be configured at boot time via properties and modified in runtime by LMS admins.
 */
@Component
public class PremiumReaderLendingPolicy implements LendingPolicy {

    public static final String POLICY_NAME = "PREMIUM";

    private int durationInDays;
    private int maxSimultaneousLendings;
    private int fineValuePerDayInCents;
    private int priority;

    @Autowired
    public PremiumReaderLendingPolicy(LendingPolicyProperties properties) {
        if (properties != null && properties.getPremium() != null) {
            this.durationInDays = properties.getPremium().getDurationInDays();
            this.maxSimultaneousLendings = properties.getPremium().getMaxSimultaneousLendings();
            this.fineValuePerDayInCents = properties.getPremium().getFineValuePerDayInCents();
        } else {
            this.durationInDays = 30;
            this.maxSimultaneousLendings = 5;
            this.fineValuePerDayInCents = 100;
        }
        this.priority = 20;
    }

    public PremiumReaderLendingPolicy() {
        this(null);
    }

    @Override
    public String getPolicyName() {
        return POLICY_NAME;
    }

    @Override
    public boolean isApplicable(ReaderDetails reader, Book book) {
        if (reader == null || reader.getReader() == null) {
            return false;
        }
        String username = reader.getReader().getUsername() != null ? reader.getReader().getUsername().toLowerCase() : "";
        String name = reader.getReader().getName() != null ? reader.getReader().getName().toString().toLowerCase() : "";
        return username.contains("premium") || username.contains("vip") || username.contains("academic")
                || name.contains("prof") || name.contains("docente") || name.contains("investigador");
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
        return "Premium reader policy (researchers/professors: " + durationInDays + " days)";
    }
}
