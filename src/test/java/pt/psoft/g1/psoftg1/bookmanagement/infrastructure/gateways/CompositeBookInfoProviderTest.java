package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.gateways;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookDetailsDTO;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookInfoProvider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Functional Opaque-Box Unit Tests for {@link CompositeBookInfoProvider} (SUT = class).
 *
 * <p>Validates multi-source fallback chaining, data aggregation/enrichment,
 * fault isolation upon provider failure, and boundary conditions.</p>
 */
@ExtendWith(MockitoExtension.class)
class CompositeBookInfoProviderTest {

    @Mock
    private BookInfoProvider primaryProvider;

    @Mock
    private BookInfoProvider secondaryProvider;

    @Test
    @DisplayName("When primary provider returns complete metadata, return immediately without calling secondary")
    void testFindBookByIsbn_CompletePrimary_DoesNotCallSecondary() {
        BookDetailsDTO completeDto = new BookDetailsDTO(
                "9780132350884",
                "Clean Code",
                "A Handbook of Agile Software Craftsmanship",
                List.of("Robert C. Martin"),
                "Computers / Software",
                "Prentice Hall",
                "2008",
                "http://example.com/cover.jpg",
                "googlebooks"
        );
        assertTrue(completeDto.isComplete());

        when(primaryProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.of(completeDto));

        CompositeBookInfoProvider composite = new CompositeBookInfoProvider(List.of(primaryProvider, secondaryProvider));

        Optional<BookDetailsDTO> result = composite.findBookByIsbn("9780132350884");

        assertTrue(result.isPresent());
        assertEquals("Clean Code", result.get().getTitle());
        assertEquals("googlebooks", result.get().getSourceProvider());

        verify(primaryProvider, times(1)).findBookByIsbn("9780132350884");
        verify(secondaryProvider, never()).findBookByIsbn(anyString());
    }

    @Test
    @DisplayName("When primary provider returns partial metadata, secondary provider is called and fields are enriched")
    void testFindBookByIsbn_PartialPrimary_EnrichedBySecondary() {
        BookDetailsDTO partialPrimary = new BookDetailsDTO(
                "9780132350884",
                "Clean Code",
                null, // Missing description
                List.of("Robert C. Martin"),
                null, // Missing genre
                null, // Missing publisher
                "2008",
                "http://example.com/cover.jpg",
                "googlebooks"
        );
        assertFalse(partialPrimary.isComplete());

        BookDetailsDTO secondaryDto = new BookDetailsDTO(
                "9780132350884",
                "Clean Code Alternative",
                "Craftsmanship description",
                List.of("Other Author"),
                "Software Engineering",
                "Prentice Hall",
                "2008",
                null,
                "openlibrary"
        );

        when(primaryProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.of(partialPrimary));
        when(secondaryProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.of(secondaryDto));

        CompositeBookInfoProvider composite = new CompositeBookInfoProvider(List.of(primaryProvider, secondaryProvider));

        Optional<BookDetailsDTO> result = composite.findBookByIsbn("9780132350884");

        assertTrue(result.isPresent());
        BookDetailsDTO merged = result.get();
        assertEquals("Clean Code", merged.getTitle()); // Kept from primary
        assertEquals("Craftsmanship description", merged.getDescription()); // Enriched from secondary
        assertEquals(List.of("Robert C. Martin"), merged.getAuthors()); // Kept from primary
        assertEquals("Software Engineering", merged.getGenre()); // Enriched from secondary
        assertEquals("Prentice Hall", merged.getPublisher()); // Enriched from secondary
        assertEquals("googlebooks+openlibrary", merged.getSourceProvider());

        verify(primaryProvider, times(1)).findBookByIsbn("9780132350884");
        verify(secondaryProvider, times(1)).findBookByIsbn("9780132350884");
    }

    @Test
    @DisplayName("When primary provider returns empty, falls back to secondary provider")
    void testFindBookByIsbn_PrimaryEmpty_FallsBackToSecondary() {
        when(primaryProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.empty());

        BookDetailsDTO secondaryDto = new BookDetailsDTO(
                "9780132350884", "Clean Code", "Desc", List.of("Uncle Bob"), "Software",
                "Prentice", "2008", null, "openlibrary"
        );
        when(secondaryProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.of(secondaryDto));

        CompositeBookInfoProvider composite = new CompositeBookInfoProvider(List.of(primaryProvider, secondaryProvider));

        Optional<BookDetailsDTO> result = composite.findBookByIsbn("9780132350884");

        assertTrue(result.isPresent());
        assertEquals("Clean Code", result.get().getTitle());
        assertEquals("openlibrary", result.get().getSourceProvider());

        verify(primaryProvider, times(1)).findBookByIsbn("9780132350884");
        verify(secondaryProvider, times(1)).findBookByIsbn("9780132350884");
    }

    @Test
    @DisplayName("When primary provider throws runtime exception, fault isolation catches it and secondary provider succeeds")
    void testFindBookByIsbn_PrimaryFailsWithException_FaultIsolationToSecondary() {
        when(primaryProvider.getProviderName()).thenReturn("failing-provider");
        when(primaryProvider.findBookByIsbn("9780132350884")).thenThrow(new RuntimeException("Connection timeout"));

        BookDetailsDTO secondaryDto = new BookDetailsDTO(
                "9780132350884", "Clean Code", "Desc", List.of("Uncle Bob"), "Software",
                "Prentice", "2008", null, "openlibrary"
        );
        when(secondaryProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.of(secondaryDto));

        CompositeBookInfoProvider composite = new CompositeBookInfoProvider(List.of(primaryProvider, secondaryProvider));

        Optional<BookDetailsDTO> result = composite.findBookByIsbn("9780132350884");

        assertTrue(result.isPresent());
        assertEquals("Clean Code", result.get().getTitle());
        assertEquals("openlibrary", result.get().getSourceProvider());

        verify(primaryProvider, times(1)).findBookByIsbn("9780132350884");
        verify(secondaryProvider, times(1)).findBookByIsbn("9780132350884");
    }

    @Test
    @DisplayName("When all providers return empty or fail, returns Optional.empty()")
    void testFindBookByIsbn_AllProvidersFail_ReturnsEmpty() {
        when(primaryProvider.getProviderName()).thenReturn("provider1");

        when(primaryProvider.findBookByIsbn("9780132350884")).thenThrow(new RuntimeException("503 Service Unavailable"));
        when(secondaryProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.empty());

        CompositeBookInfoProvider composite = new CompositeBookInfoProvider(List.of(primaryProvider, secondaryProvider));

        Optional<BookDetailsDTO> result = composite.findBookByIsbn("9780132350884");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Boundary tests: null or empty inputs")
    void testBoundaryConditions() {
        // Empty provider list
        CompositeBookInfoProvider emptyComposite = new CompositeBookInfoProvider(Collections.emptyList());
        assertTrue(emptyComposite.findBookByIsbn("9780132350884").isEmpty());
        assertEquals("composite(none)", emptyComposite.getProviderName());
        assertTrue(emptyComposite.getProviders().isEmpty());

        // Null provider list
        CompositeBookInfoProvider nullListComposite = new CompositeBookInfoProvider(null);
        assertTrue(nullListComposite.findBookByIsbn("9780132350884").isEmpty());

        // Null or blank ISBN
        CompositeBookInfoProvider validComposite = new CompositeBookInfoProvider(List.of(primaryProvider));
        assertTrue(validComposite.findBookByIsbn(null).isEmpty());
        assertTrue(validComposite.findBookByIsbn("").isEmpty());
        assertTrue(validComposite.findBookByIsbn("   ").isEmpty());
        verify(primaryProvider, never()).findBookByIsbn(any());

        // Provider name joined
        when(primaryProvider.getProviderName()).thenReturn("googlebooks");
        assertEquals("composite(googlebooks)", validComposite.getProviderName());
    }
}
