package pt.psoft.g1.psoftg1.bookmanagement.services;

import java.util.Optional;

/**
 * Output Port (Gateway Contract) for retrieving bibliographic information about books
 * from external systems (e.g., Google Books, Open Library, or future sources).
 *
 * <p>Satisfies the Open-Closed Principle (OCP) and Dependency Inversion Principle (DIP):
 * application services depend exclusively on this abstraction while concrete external
 * integrations are provided by infrastructure adapters selected at setup time.</p>
 */
public interface BookInfoProvider {

    /**
     * Retrieves normalized bibliographic metadata for a given ISBN.
     *
     * @param isbn the ISBN-10 or ISBN-13 identifier of the book
     * @return an {@link Optional} containing the normalized {@link BookDetailsDTO} if found,
     *         or {@link Optional#empty()} if the book was not found or the source is unreachable
     */
    Optional<BookDetailsDTO> findBookByIsbn(String isbn);

    /**
     * Unique logical identifier of the bibliographic information source (e.g., "googlebooks", "openlibrary").
     *
     * @return the provider identifier used in setup-time configuration
     */
    String getProviderName();
}

