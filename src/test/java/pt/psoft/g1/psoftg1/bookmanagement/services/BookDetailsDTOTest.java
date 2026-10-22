package pt.psoft.g1.psoftg1.bookmanagement.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Transparent-Box and Opaque-Box Unit Tests for {@link BookDetailsDTO}.
 *
 * <p>Validates domain rules, normalization, immutability, completeness criteria,
 * fallback merging logic, and equality contracts.</p>
 */
class BookDetailsDTOTest {

    @Test
    @DisplayName("Should create normalized BookDetailsDTO with valid parameters")
    void testConstructor_ValidParameters_NormalizesStrings() {
        BookDetailsDTO dto = new BookDetailsDTO(
                "  978-0132350884  ",
                " Clean Code ",
                " A Handbook of Agile Software Craftsmanship ",
                Arrays.asList(" Robert C. Martin ", " Dean Wampler ", null, "   "),
                " Computers / Software ",
                " Prentice Hall ",
                " 2008-08-01 ",
                " http://example.com/cover.jpg ",
                " googlebooks "
        );

        assertEquals("978-0132350884", dto.getIsbn());
        assertEquals("Clean Code", dto.getTitle());
        assertEquals("A Handbook of Agile Software Craftsmanship", dto.getDescription());
        assertEquals(List.of("Robert C. Martin", "Dean Wampler"), dto.getAuthors());
        assertEquals("Computers / Software", dto.getGenre());
        assertEquals("Prentice Hall", dto.getPublisher());
        assertEquals("2008-08-01", dto.getPublishedDate());
        assertEquals("http://example.com/cover.jpg", dto.getThumbnailUrl());
        assertEquals("googlebooks", dto.getSourceProvider());
    }

    @Test
    @DisplayName("Should normalize null and empty/blank strings to null")
    void testConstructor_BlankStrings_NormalizedToNull() {
        BookDetailsDTO dto = new BookDetailsDTO(
                "",
                "   ",
                null,
                null,
                "\t",
                " ",
                null,
                "",
                null
        );

        assertNull(dto.getIsbn());
        assertNull(dto.getTitle());
        assertNull(dto.getDescription());
        assertNotNull(dto.getAuthors());
        assertTrue(dto.getAuthors().isEmpty());
        assertNull(dto.getGenre());
        assertNull(dto.getPublisher());
        assertNull(dto.getPublishedDate());
        assertNull(dto.getThumbnailUrl());
        assertNull(dto.getSourceProvider());
    }

    @Test
    @DisplayName("Convenience constructor should populate core fields and leave optional fields null")
    void testConvenienceConstructor() {
        BookDetailsDTO dto = new BookDetailsDTO(
                "9780132350884",
                "Clean Code",
                "Craftsmanship",
                List.of("Uncle Bob"),
                "Software"
        );

        assertEquals("9780132350884", dto.getIsbn());
        assertEquals("Clean Code", dto.getTitle());
        assertEquals("Craftsmanship", dto.getDescription());
        assertEquals(List.of("Uncle Bob"), dto.getAuthors());
        assertEquals("Software", dto.getGenre());
        assertNull(dto.getPublisher());
        assertNull(dto.getPublishedDate());
        assertNull(dto.getThumbnailUrl());
        assertNull(dto.getSourceProvider());
    }

    @Test
    @DisplayName("isComplete() should return true only when title, description, authors, and genre are present")
    void testIsComplete_EvaluatesRequiredFields() {
        // Complete
        BookDetailsDTO complete = new BookDetailsDTO(
                "9780132350884", "Clean Code", "Description", List.of("Uncle Bob"), "Software"
        );
        assertTrue(complete.isComplete());

        // Missing title
        BookDetailsDTO noTitle = new BookDetailsDTO(
                "9780132350884", null, "Description", List.of("Uncle Bob"), "Software"
        );
        assertFalse(noTitle.isComplete());

        // Blank title
        BookDetailsDTO blankTitle = new BookDetailsDTO(
                "9780132350884", "   ", "Description", List.of("Uncle Bob"), "Software"
        );
        assertFalse(blankTitle.isComplete());

        // Missing description
        BookDetailsDTO noDesc = new BookDetailsDTO(
                "9780132350884", "Clean Code", null, List.of("Uncle Bob"), "Software"
        );
        assertFalse(noDesc.isComplete());

        // Empty authors
        BookDetailsDTO noAuthors = new BookDetailsDTO(
                "9780132350884", "Clean Code", "Description", List.of(), "Software"
        );
        assertFalse(noAuthors.isComplete());

        // Missing genre
        BookDetailsDTO noGenre = new BookDetailsDTO(
                "9780132350884", "Clean Code", "Description", List.of("Uncle Bob"), null
        );
        assertFalse(noGenre.isComplete());
    }

    @Test
    @DisplayName("mergeWith(null) should return the exact same instance")
    void testMergeWith_NullFallback_ReturnsSame() {
        BookDetailsDTO primary = new BookDetailsDTO(
                "9780132350884", "Clean Code", "Desc", List.of("Author"), "Genre"
        );

        assertSame(primary, primary.mergeWith(null));
    }

    @Test
    @DisplayName("mergeWith should prioritize primary fields and fill missing fields from fallback")
    void testMergeWith_FillsMissingFieldsFromFallback() {
        BookDetailsDTO primary = new BookDetailsDTO(
                "9780132350884",
                "Clean Code",
                null, // missing
                List.of("Robert C. Martin"),
                null, // missing
                null, // missing
                "2008-08-01",
                "http://example.com/cover.jpg",
                "googlebooks"
        );

        BookDetailsDTO fallback = new BookDetailsDTO(
                "9780132350884",
                "Clean Code: Alternative Title",
                "A Handbook of Software",
                List.of("Dean Wampler"),
                "Software Engineering",
                "Prentice Hall",
                "2008",
                "http://example.com/fallback.jpg",
                "openlibrary"
        );

        BookDetailsDTO merged = primary.mergeWith(fallback);

        assertEquals("9780132350884", merged.getIsbn());
        assertEquals("Clean Code", merged.getTitle()); // Kept from primary
        assertEquals("A Handbook of Software", merged.getDescription()); // Enriched from fallback
        assertEquals(List.of("Robert C. Martin"), merged.getAuthors()); // Kept from primary
        assertEquals("Software Engineering", merged.getGenre()); // Enriched from fallback
        assertEquals("Prentice Hall", merged.getPublisher()); // Enriched from fallback
        assertEquals("2008-08-01", merged.getPublishedDate()); // Kept from primary
        assertEquals("http://example.com/cover.jpg", merged.getThumbnailUrl()); // Kept from primary
        assertEquals("googlebooks+openlibrary", merged.getSourceProvider()); // Combined
    }

    @Test
    @DisplayName("mergeWith should preserve fallback source when primary source is missing or identical")
    void testMergeWith_SourceProviderCombinations() {
        // Case 1: Primary source has null source provider
        BookDetailsDTO noSourcePrimary = new BookDetailsDTO("123", "Title", null, List.of("A"), "G", null, null, null, null);
        BookDetailsDTO fallback = new BookDetailsDTO("123", null, "Desc", null, null, null, null, null, "openlibrary");
        assertEquals("openlibrary", noSourcePrimary.mergeWith(fallback).getSourceProvider());

        // Case 2: Both have same source provider -> no duplicate plus sign
        BookDetailsDTO sameSourcePrimary = new BookDetailsDTO("123", "Title", null, List.of("A"), "G", null, null, null, "googlebooks");
        BookDetailsDTO sameSourceFallback = new BookDetailsDTO("123", null, "Desc", null, null, null, null, null, "googlebooks");
        assertEquals("googlebooks", sameSourcePrimary.mergeWith(sameSourceFallback).getSourceProvider());

        // Case 3: Primary has authors empty, fallback has authors -> fallback used
        BookDetailsDTO emptyAuthors = new BookDetailsDTO("123", "Title", null, List.of(), "G", null, null, null, "src1");
        BookDetailsDTO fallbackAuthors = new BookDetailsDTO("123", null, null, List.of("Author2"), null, null, null, null, "src2");
        assertEquals(List.of("Author2"), emptyAuthors.mergeWith(fallbackAuthors).getAuthors());
    }

    @Test
    @DisplayName("equals and hashCode contracts verification")
    void testEqualsAndHashCode() {
        BookDetailsDTO dto1 = new BookDetailsDTO(
                "9780132350884", "Clean Code", "Desc", List.of("Author 1"), "Genre",
                "Publisher", "2008", "http://thumb.jpg", "googlebooks"
        );
        BookDetailsDTO dto2 = new BookDetailsDTO(
                "9780132350884", "Clean Code", "Desc", List.of("Author 1"), "Genre",
                "Publisher", "2008", "http://thumb.jpg", "googlebooks"
        );
        BookDetailsDTO differentIsbn = new BookDetailsDTO(
                "9781111111111", "Clean Code", "Desc", List.of("Author 1"), "Genre",
                "Publisher", "2008", "http://thumb.jpg", "googlebooks"
        );

        // Reflexive
        assertEquals(dto1, dto1);
        // Symmetric
        assertEquals(dto1, dto2);
        assertEquals(dto2, dto1);
        assertEquals(dto1.hashCode(), dto2.hashCode());

        // Not equal
        assertNotEquals(dto1, differentIsbn);
        assertNotEquals(dto1, null);
        assertNotEquals(dto1, "Different Type");
    }
}

