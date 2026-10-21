package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.gateways;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookDetailsDTO;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookInfoProvider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Composite and Fallback/Enrichment Chain implementation of {@link BookInfoProvider}.
 *
 * <p>Iterates over the ordered list of bibliographic information providers selected at setup time:
 * <ul>
 *   <li><b>Fallback (Fault Isolation):</b> If a provider fails with a runtime exception or returns
 *       {@link Optional#empty()}, the chain transparently continues to the next configured source.</li>
 *   <li><b>Data Enrichment (Multi-Source Aggregation):</b> Since external systems expose different
 *       subsets of bibliographic metadata, if the primary source returns a partial record
 *       (e.g., missing description or genre), subsequent configured sources are queried to fill in
 *       the missing fields via {@link BookDetailsDTO#mergeWith(BookDetailsDTO)}.</li>
 * </ul>
 */
public class CompositeBookInfoProvider implements BookInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(CompositeBookInfoProvider.class);

    private final List<BookInfoProvider> providers;

    public CompositeBookInfoProvider(List<BookInfoProvider> providers) {
        if (providers == null) {
            this.providers = Collections.emptyList();
        } else {
            this.providers = providers.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    @Override
    public Optional<BookDetailsDTO> findBookByIsbn(String isbn) {
        if (isbn == null || isbn.trim().isEmpty() || providers.isEmpty()) {
            return Optional.empty();
        }

        BookDetailsDTO accumulated = null;

        for (BookInfoProvider provider : providers) {
            try {
                Optional<BookDetailsDTO> candidate = provider.findBookByIsbn(isbn);
                if (candidate.isPresent()) {
                    BookDetailsDTO currentDto = candidate.get();
                    if (accumulated == null) {
                        accumulated = currentDto;
                    } else {
                        accumulated = accumulated.mergeWith(currentDto);
                    }

                    if (accumulated.isComplete()) {
                        return Optional.of(accumulated);
                    }
                }
            } catch (Exception ex) {
                log.warn("Bibliographic provider '{}' failed for ISBN {}: {}",
                        provider.getProviderName(), isbn, ex.getMessage());
            }
        }

        return Optional.ofNullable(accumulated);
    }

    @Override
    public String getProviderName() {
        if (providers.isEmpty()) {
            return "composite(none)";
        }
        String joined = providers.stream()
                .map(BookInfoProvider::getProviderName)
                .collect(Collectors.joining(","));
        return "composite(" + joined + ")";
    }

    public List<BookInfoProvider> getProviders() {
        return providers;
    }
}

