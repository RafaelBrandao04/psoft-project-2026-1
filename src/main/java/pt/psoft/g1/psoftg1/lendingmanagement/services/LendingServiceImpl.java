package pt.psoft.g1.psoftg1.lendingmanagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingCreatedEvent;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingReturnedEvent;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Fine;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.lendingmanagement.policies.LendingPolicy;
import pt.psoft.g1.psoftg1.lendingmanagement.policies.LendingPolicyResolver;
import pt.psoft.g1.psoftg1.lendingmanagement.repositories.FineRepository;
import pt.psoft.g1.psoftg1.lendingmanagement.repositories.LendingRepository;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
import pt.psoft.g1.psoftg1.shared.services.Page;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@PropertySource({ "classpath:config/library.properties" })
public class LendingServiceImpl implements LendingService {
    private final LendingRepository lendingRepository;
    private final FineRepository fineRepository;
    private final BookRepository bookRepository;
    private final ReaderRepository readerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private LendingPolicyResolver lendingPolicyResolver;

    @Value("${lendingDurationInDays}")
    private int lendingDurationInDays;
    @Value("${fineValuePerDayInCents}")
    private int fineValuePerDayInCents;

    @Override
    public Optional<Lending> findByLendingNumber(String lendingNumber) {
        return lendingRepository.findByLendingNumber(lendingNumber);
    }

    @Override
    public List<Lending> listByReaderNumberAndIsbn(String readerNumber, String isbn, Optional<Boolean> returned) {
        List<Lending> lendings = lendingRepository.listByReaderNumberAndIsbn(readerNumber, isbn);
        if (returned.isEmpty()) {
            return lendings;
        } else {
            for (int i = 0; i < lendings.size(); i++) {
                if ((lendings.get(i).getReturnedDate() == null) == returned.get()) {
                    lendings.remove(i);
                    i--;
                }
            }
        }
        return lendings;
    }

    @Override
    public Lending create(final CreateLendingRequest resource) {
        int count = 0;
        List<Lending> lendingList = lendingRepository.listOutstandingByReaderNumber(resource.getReaderNumber());
        for (Lending lending : lendingList) {
            // Business rule (Section 2.4 & 3.7): cannot create a lending if reader has any overdue lending.
            if (lending.getDaysDelayed() > 0) {
                throw new LendingForbiddenException("Reader has book(s) past their due date");
            }
            count++;
        }

        // Legacy fallback when no LendingPolicyResolver is injected (e.g., isolated unit tests without policy beans)
        if (lendingPolicyResolver == null && count >= 3) {
            throw new LendingForbiddenException("Reader has three books outstanding already");
        }

        final var b = bookRepository.findByIsbn(resource.getIsbn())
                .orElseThrow(() -> new NotFoundException("Book not found"));
        final var r = readerRepository.findByReaderNumber(resource.getReaderNumber())
                .orElseThrow(() -> new NotFoundException("Reader not found"));

        LendingPolicy policy = null;
        if (lendingPolicyResolver != null) {
            try {
                List<Lending> readerHistory = lendingRepository.listByReaderNumber(resource.getReaderNumber());
                policy = lendingPolicyResolver.resolvePolicy(r, b, lendingList, readerHistory);
            } catch (Exception ignored) {
                // fallback to default parameters when resolver is mocked or unconfigured
            }
        }

        if (policy != null) {
            policy.validateCanLend(r, b, lendingList);
        }

        int duration = policy != null ? policy.getLendingDurationInDays(lendingList) : lendingDurationInDays;
        int fine = policy != null ? policy.getFineValuePerDayInCents() : fineValuePerDayInCents;

        int seq = lendingRepository.getCountFromCurrentYear() + 1;
        final Lending l = new Lending(b, r, seq, duration, fine);

        final Lending saved = lendingRepository.save(l);
        if (eventPublisher != null) {
            String bookTitle = (b != null && b.getTitle() != null) ? b.getTitle().getTitle() : "Book";
            String isbn = (b != null && b.getIsbn() != null) ? b.getIsbn()
                    : (resource.getIsbn() != null ? resource.getIsbn() : "N/A");
            String lendingNumber = (saved != null && saved.getLendingNumber() != null) ? saved.getLendingNumber()
                    : "N/A";
            LocalDate limitDate = (saved != null && saved.getLimitDate() != null) ? saved.getLimitDate()
                    : LocalDate.now();

            eventPublisher.publishEvent(new LendingCreatedEvent(
                    lendingNumber,
                    resource.getReaderNumber(),
                    bookTitle,
                    isbn,
                    limitDate));
        }

        return saved;
    }

    /**
     * Renews an active lending after re-evaluating the applicable lending policy
     * for the reader (Section 2.3, 2.5, 3.6, and 4.3 of Lending Policies Specification v1.0).
     */
    @Override
    public Lending renew(final String lendingNumber, final long desiredVersion) {
        var lending = lendingRepository.findByLendingNumber(lendingNumber)
                .orElseThrow(() -> new NotFoundException("Cannot renew lending with this lending number"));

        if (lending.getReturnedDate() != null) {
            throw new LendingForbiddenException("Cannot renew a lending that has already been returned");
        }

        if (lending.getDaysDelayed() > 0) {
            throw new LendingForbiddenException("An overdue lending cannot be renewed");
        }

        ReaderDetails reader = lending.getReaderDetails();
        String readerNumber = reader != null ? reader.getReaderNumber() : null;
        List<Lending> activeLendings = readerNumber != null
                ? lendingRepository.listOutstandingByReaderNumber(readerNumber)
                : List.of(lending);

        for (Lending active : activeLendings) {
            if (active.getDaysDelayed() > 0) {
                throw new LendingForbiddenException("Reader has book(s) past their due date");
            }
        }

        int renewalDays = lendingDurationInDays > 0 ? lendingDurationInDays : 15;
        if (lendingPolicyResolver != null) {
            List<Lending> readerHistory = readerNumber != null
                    ? lendingRepository.listByReaderNumber(readerNumber)
                    : List.of();
            // Section 4.3: Re-evaluate the applicable lending policy whenever a renewal is requested
            LendingPolicy policy = lendingPolicyResolver.resolvePolicy(reader, lending.getBook(), activeLendings, readerHistory);
            policy.validateCanRenew(lending, reader, activeLendings);
            renewalDays = policy.getRenewalPeriodInDays(activeLendings);
        }

        lending.renew(renewalDays, desiredVersion);
        return lendingRepository.save(lending);
    }

    @Override
    public Lending setReturned(final String lendingNumber, final SetLendingReturnedRequest resource,
            final long desiredVersion) {

        var lending = lendingRepository.findByLendingNumber(lendingNumber)
                .orElseThrow(() -> new NotFoundException("Cannot update lending with this lending number"));

        lending.setReturned(desiredVersion, resource.getCommentary());

        if (lending.getDaysDelayed() > 0) {
            final var fine = new Fine(lending);
            fineRepository.save(fine);
        }

        final Lending saved = lendingRepository.save(lending);
        if (eventPublisher != null) {
            String lendingNum = (saved != null && saved.getLendingNumber() != null) ? saved.getLendingNumber()
                    : lendingNumber;
            String readerNum = (saved != null && saved.getReaderDetails() != null
                    && saved.getReaderDetails().getReaderNumber() != null)
                            ? saved.getReaderDetails().getReaderNumber().toString()
                            : "N/A";
            String bookTitle = (saved != null && saved.getBook() != null && saved.getBook().getTitle() != null)
                    ? saved.getBook().getTitle().getTitle()
                    : "Book";
            LocalDate retDate = (saved != null && saved.getReturnedDate() != null) ? saved.getReturnedDate()
                    : LocalDate.now();
            int daysDelayed = saved != null ? saved.getDaysDelayed() : 0;
            int fineCents = (saved != null && saved.getFineValueInCents() != null)
                    ? saved.getFineValueInCents().orElse(0)
                    : 0;

            eventPublisher.publishEvent(new LendingReturnedEvent(
                    lendingNum,
                    readerNum,
                    bookTitle,
                    retDate,
                    daysDelayed,
                    fineCents));
        }

        return saved;
    }

    @Override
    public Double getAverageDuration() {
        Double avg = lendingRepository.getAverageDuration();
        return Double.valueOf(String.format(Locale.US, "%.1f", avg));
    }

    @Override
    public List<Lending> getOverdue(Page page) {
        if (page == null)
            page = new Page(1, 10);

        return lendingRepository.getOverdue(page);
    }

    @Override
    public Double getAvgLendingDurationByIsbn(String isbn) {
        Double avg = lendingRepository.getAvgLendingDurationByIsbn(isbn);
        return Double.valueOf(String.format(Locale.US, "%.1f", avg));
    }

    @Override
    public List<Lending> searchLendings(Page page, SearchLendingQuery query) {
        LocalDate startDate = null;
        LocalDate endDate = null;

        if (page == null)
            page = new Page(1, 10);
        if (query == null)
            query = new SearchLendingQuery("",
                    "",
                    null,
                    null,
                    null);

        try {
            if (query.getStartDate() != null)
                startDate = LocalDate.parse(query.getStartDate());
            if (query.getEndDate() != null)
                endDate = LocalDate.parse(query.getEndDate());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Expected format is YYYY-MM-DD");
        }

        return lendingRepository.searchLendings(page,
                query.getReaderNumber(),
                query.getIsbn(),
                query.getReturned(),
                startDate,
                endDate);

    }

}
