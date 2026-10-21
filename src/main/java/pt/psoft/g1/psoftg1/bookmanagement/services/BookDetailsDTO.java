package pt.psoft.g1.psoftg1.bookmanagement.services;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Canonical Data Model (DTO) representing normalized bibliographic metadata
 * retrieved from external bibliographic information sources (e.g., Google Books, Open Library).
 */
public class BookDetailsDTO {

    private final String isbn;
    private final String title;
    private final String description;
    private final List<String> authors;
    private final String genre;
    private final String publisher;
    private final String publishedDate;
    private final String thumbnailUrl;
    private final String sourceProvider;

    public BookDetailsDTO(
            String isbn,
            String title,
            String description,
            List<String> authors,
            String genre,
            String publisher,
            String publishedDate,
            String thumbnailUrl,
            String sourceProvider) {
        this.isbn = normalize(isbn);
        this.title = normalize(title);
        this.description = normalize(description);
        this.authors = (authors == null)
                ? Collections.emptyList()
                : authors.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        this.genre = normalize(genre);
        this.publisher = normalize(publisher);
        this.publishedDate = normalize(publishedDate);
        this.thumbnailUrl = normalize(thumbnailUrl);
        this.sourceProvider = normalize(sourceProvider);
    }

    public BookDetailsDTO(
            String isbn,
            String title,
            String description,
            List<String> authors,
            String genre) {
        this(isbn, title, description, authors, genre, null, null, null, null);
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public String getGenre() {
        return genre;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getPublishedDate() {
        return publishedDate;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    /**
     * Checks whether the essential bibliographic fields (title, description, authors, genre)
     * are populated.
     */
    public boolean isComplete() {
        return hasText(title)
                && hasText(description)
                && !authors.isEmpty()
                && hasText(genre);
    }

    /**
     * Merges this bibliographic record (primary source) with a fallback record (secondary source),
     * preserving non-empty values from this instance and filling missing fields from the fallback.
     */
    public BookDetailsDTO mergeWith(BookDetailsDTO fallback) {
        if (fallback == null) {
            return this;
        }

        String mergedIsbn = hasText(this.isbn) ? this.isbn : fallback.isbn;
        String mergedTitle = hasText(this.title) ? this.title : fallback.title;
        String mergedDescription = hasText(this.description) ? this.description : fallback.description;
        List<String> mergedAuthors = !this.authors.isEmpty() ? this.authors : fallback.authors;
        String mergedGenre = hasText(this.genre) ? this.genre : fallback.genre;
        String mergedPublisher = hasText(this.publisher) ? this.publisher : fallback.publisher;
        String mergedPublishedDate = hasText(this.publishedDate) ? this.publishedDate : fallback.publishedDate;
        String mergedThumbnail = hasText(this.thumbnailUrl) ? this.thumbnailUrl : fallback.thumbnailUrl;

        String mergedSource = this.sourceProvider;
        if (hasText(fallback.sourceProvider)) {
            if (!hasText(mergedSource)) {
                mergedSource = fallback.sourceProvider;
            } else if (!mergedSource.contains(fallback.sourceProvider)) {
                mergedSource = mergedSource + "+" + fallback.sourceProvider;
            }
        }

        return new BookDetailsDTO(
                mergedIsbn,
                mergedTitle,
                mergedDescription,
                mergedAuthors,
                mergedGenre,
                mergedPublisher,
                mergedPublishedDate,
                mergedThumbnail,
                mergedSource
        );
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookDetailsDTO that)) {
            return false;
        }
        return Objects.equals(isbn, that.isbn)
                && Objects.equals(title, that.title)
                && Objects.equals(description, that.description)
                && Objects.equals(authors, that.authors)
                && Objects.equals(genre, that.genre)
                && Objects.equals(publisher, that.publisher)
                && Objects.equals(publishedDate, that.publishedDate)
                && Objects.equals(thumbnailUrl, that.thumbnailUrl)
                && Objects.equals(sourceProvider, that.sourceProvider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                isbn, title, description, authors, genre,
                publisher, publishedDate, thumbnailUrl, sourceProvider);
    }
}

