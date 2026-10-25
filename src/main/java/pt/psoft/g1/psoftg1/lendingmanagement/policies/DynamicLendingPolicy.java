package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;

import java.util.List;
import java.util.Objects;

/**
 * Dynamic Lending Policy created and managed at runtime by an LMS administrator.
 * Allows custom rule criteria based on active lendings thresholds, reader keyword,
 * book genre, custom durations, limits, and priorities.
 */
public class DynamicLendingPolicy implements LendingPolicy {

    private final String policyName;
    private String description;
    private int durationInDays;
    private int maxSimultaneousLendings;
    private int fineValuePerDayInCents;
    private int priority;

    // Optional dynamic matching rules
    private Integer minActiveLendings;
    private Integer maxActiveLendings;
    private String readerTypeKeyword;
    private String bookGenre;

    public DynamicLendingPolicy(String policyName,
                                String description,
                                int durationInDays,
                                int maxSimultaneousLendings,
                                int fineValuePerDayInCents,
                                int priority,
                                Integer minActiveLendings,
                                Integer maxActiveLendings,
                                String readerTypeKeyword,
                                String bookGenre) {
        this.policyName = Objects.requireNonNull(policyName, "Policy name cannot be null");
        this.description = description != null ? description : policyName;
        this.durationInDays = durationInDays > 0 ? durationInDays : 15;
        this.maxSimultaneousLendings = maxSimultaneousLendings > 0 ? maxSimultaneousLendings : 5;
        this.fineValuePerDayInCents = fineValuePerDayInCents >= 0 ? fineValuePerDayInCents : 200;
        this.priority = priority;
        this.minActiveLendings = minActiveLendings;
        this.maxActiveLendings = maxActiveLendings;
        this.readerTypeKeyword = readerTypeKeyword;
        this.bookGenre = bookGenre;
    }

    @Override
    public String getPolicyName() {
        return policyName;
    }

    @Override
    public boolean isApplicable(ReaderDetails reader, Book book) {
        return isApplicable(reader, book, null);
    }

    @Override
    public boolean isApplicable(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        int activeCount = activeLendings != null ? activeLendings.size() : 0;

        if (minActiveLendings != null && activeCount < minActiveLendings) {
            return false;
        }

        if (maxActiveLendings != null && activeCount > maxActiveLendings) {
            return false;
        }

        if (readerTypeKeyword != null && !readerTypeKeyword.isBlank()) {
            if (reader == null || reader.getReader() == null) {
                return false;
            }
            String username = reader.getReader().getUsername() != null ? reader.getReader().getUsername().toLowerCase() : "";
            String name = reader.getReader().getName() != null ? reader.getReader().getName().toString().toLowerCase() : "";
            String kw = readerTypeKeyword.toLowerCase();
            if (!username.contains(kw) && !name.contains(kw)) {
                return false;
            }
        }

        if (bookGenre != null && !bookGenre.isBlank()) {
            if (book == null || book.getGenre() == null || book.getGenre().getGenre() == null) {
                return false;
            }
            if (!book.getGenre().getGenre().equalsIgnoreCase(bookGenre)) {
                return false;
            }
        }

        return true;
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
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMinActiveLendings() {
        return minActiveLendings;
    }

    public void setMinActiveLendings(Integer minActiveLendings) {
        this.minActiveLendings = minActiveLendings;
    }

    public Integer getMaxActiveLendings() {
        return maxActiveLendings;
    }

    public void setMaxActiveLendings(Integer maxActiveLendings) {
        this.maxActiveLendings = maxActiveLendings;
    }

    public String getReaderTypeKeyword() {
        return readerTypeKeyword;
    }

    public void setReaderTypeKeyword(String readerTypeKeyword) {
        this.readerTypeKeyword = readerTypeKeyword;
    }

    public String getBookGenre() {
        return bookGenre;
    }

    public void setBookGenre(String bookGenre) {
        this.bookGenre = bookGenre;
    }
}

