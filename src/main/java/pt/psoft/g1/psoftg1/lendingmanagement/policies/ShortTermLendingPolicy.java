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
 * Short-Term Lending Policy.
 * Applies when a reader currently has 3 or more active lendings (borrowing book #4 or more),
 * or when the book requested is a reference / short-term consultation work.
 * Grants a shorter return period (e.g. 7 days).
 *
 * Parameters can be configured at boot time via properties and modified in runtime by LMS admins.
 */
@Component
public class ShortTermLendingPolicy implements LendingPolicy {

    public static final String POLICY_NAME = "SHORT_TERM";

    private int durationInDays;
    private int maxSimultaneousLendings;
    private int fineValuePerDayInCents;
    private int priority;

    @Autowired
    public ShortTermLendingPolicy(LendingPolicyProperties properties) {
        if (properties != null && properties.getShortTerm() != null) {
            this.durationInDays = properties.getShortTerm().getDurationInDays();
            this.maxSimultaneousLendings = properties.getShortTerm().getMaxSimultaneousLendings();
            this.fineValuePerDayInCents = properties.getShortTerm().getFineValuePerDayInCents();
        } else {
            this.durationInDays = 7;
            this.maxSimultaneousLendings = 5;
            this.fineValuePerDayInCents = 400;
        }
        this.priority = 30;
    }

    public ShortTermLendingPolicy() {
        this(null);
    }

    @Override
    public String getPolicyName() {
        return POLICY_NAME;
    }

    @Override
    public boolean isApplicable(ReaderDetails reader, Book book) {
        return isReferenceBook(book);
    }

    @Override
    public boolean isApplicable(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        // Applies if reader has 3 or more active lendings (taking 4th or more book),
        // or if the book itself is a reference/short-term work.
        boolean highActiveVolume = activeLendings != null && activeLendings.size() >= 3;
        return highActiveVolume || isReferenceBook(book);
    }

    private boolean isReferenceBook(Book book) {
        if (book == null) {
            return false;
        }
        if (book.getGenre() != null && book.getGenre().getGenre() != null) {
            String g = book.getGenre().getGenre().toLowerCase();
            if (g.contains("reference") || g.contains("referência") || g.contains("dicionário") || g.contains("enciclopédia")) {
                return true;
            }
        }
        if (book.getTitle() != null) {
            String t = book.getTitle().toString().toLowerCase();
            return t.contains("reference") || t.contains("referência") || t.contains("manual");
        }
        return false;
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
        return "Short-term policy (4th+ books or reference books: " + durationInDays + " days)";
    }
}
