package pt.psoft.g1.psoftg1.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.gateways.CompositeBookInfoProvider;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookDetailsDTO;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookInfoProvider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Functional and Structural Tests for {@link BibliographicConfig} (SUT = configuration class).
 *
 * <p>Validates setup-time provider selection, priority ordering, deduplication,
 * and dynamic reflective provider resolution (OCP), mirroring the professor's
 * {@code CatalogueTest} conventions in the {@code SmartHome} reference project.</p>
 */
class BibliographicConfigTest {

    private final BibliographicConfig config = new BibliographicConfig();

    static class TestDummyProvider implements BookInfoProvider {
        @Override
        public Optional<BookDetailsDTO> findBookByIsbn(String isbn) {
            return Optional.empty();
        }

        @Override
        public String getProviderName() {
            return "testdummy";
        }
    }

    static class NamedDummyProvider implements BookInfoProvider {
        private final String name;

        NamedDummyProvider(String name) {
            this.name = name;
        }

        @Override
        public Optional<BookDetailsDTO> findBookByIsbn(String isbn) {
            return Optional.empty();
        }

        @Override
        public String getProviderName() {
            return name;
        }
    }

    @Test
    @DisplayName("Should resolve providers in the exact priority order configured at setup time")
    void testResolveProviders_ConfiguredOrderPreserved() {
        BookInfoProvider googleMock = new NamedDummyProvider("googlebooks");
        BookInfoProvider openLibMock = new NamedDummyProvider("openlibrary");

        List<BookInfoProvider> available = List.of(googleMock, openLibMock);

        // Order 1: openlibrary first, then googlebooks
        List<BookInfoProvider> result1 = config.resolveProviders(List.of("openlibrary", "googlebooks"), available);
        assertEquals(2, result1.size());
        assertSame(openLibMock, result1.get(0));
        assertSame(googleMock, result1.get(1));

        // Order 2: googlebooks only
        List<BookInfoProvider> result2 = config.resolveProviders(List.of("googlebooks"), available);
        assertEquals(1, result2.size());
        assertSame(googleMock, result2.get(0));
    }

    @Test
    @DisplayName("Should resolve provider by case-insensitive name or simple class name")
    void testResolveProviders_CaseInsensitiveAndSimpleName() {
        TestDummyProvider dummy = new TestDummyProvider();
        List<BookInfoProvider> available = List.of(dummy);

        // Case-insensitive token
        List<BookInfoProvider> result = config.resolveProviders(List.of("TESTDUMMY"), available);
        assertEquals(1, result.size());
        assertSame(dummy, result.get(0));

        // Simple class name
        List<BookInfoProvider> resultSimple = config.resolveProviders(List.of("TestDummyProvider"), available);
        assertEquals(1, resultSimple.size());
        assertSame(dummy, resultSimple.get(0));
    }

    @Test
    @DisplayName("Should dynamically instantiate provider by fully qualified class name via reflection (OCP)")
    void testResolveProviders_ReflectiveInstantiation() {
        String className = "pt.psoft.g1.psoftg1.configuration.BibliographicConfigTest$TestDummyProvider";

        List<BookInfoProvider> result = config.resolveProviders(List.of(className), Collections.emptyList());
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("testdummy", result.get(0).getProviderName());
    }

    @Test
    @DisplayName("Should ignore unrecognized or invalid provider tokens gracefully without failing startup")
    void testResolveProviders_IgnoresInvalidTokens() {
        TestDummyProvider dummy = new TestDummyProvider();

        List<BookInfoProvider> result = config.resolveProviders(
                List.of("invalid_token", "   ", "", "testdummy"),
                List.of(dummy)
        );

        assertEquals(1, result.size());
        assertSame(dummy, result.get(0));
    }

    @Test
    @DisplayName("Should deduplicate repeated provider tokens")
    void testResolveProviders_DeduplicatesTokens() {
        TestDummyProvider dummy = new TestDummyProvider();

        List<BookInfoProvider> result = config.resolveProviders(
                List.of("testdummy", "testdummy", "TESTDUMMY"),
                List.of(dummy)
        );

        assertEquals(1, result.size());
        assertSame(dummy, result.get(0));
    }

    @Test
    @DisplayName("bookInfoProvider bean method should return CompositeBookInfoProvider with resolved providers")
    void testBookInfoProviderBeanMethod() {
        TestDummyProvider dummy = new TestDummyProvider();

        ReflectionTestUtils.setField(config, "configuredProviders", List.of("testdummy"));

        BookInfoProvider primaryBean = config.bookInfoProvider(List.of(dummy));

        assertNotNull(primaryBean);
        assertTrue(primaryBean instanceof CompositeBookInfoProvider);
        assertEquals("composite(testdummy)", primaryBean.getProviderName());
    }

    @Test
    @DisplayName("Should resolve crossref provider token alongside googlebooks and openlibrary")
    void testResolveProviders_WithCrossRef() {
        BookInfoProvider googleMock = new NamedDummyProvider("googlebooks");
        BookInfoProvider openLibMock = new NamedDummyProvider("openlibrary");
        BookInfoProvider crossRefMock = new NamedDummyProvider("crossref");

        List<BookInfoProvider> available = List.of(googleMock, openLibMock, crossRefMock);

        List<BookInfoProvider> result = config.resolveProviders(
                List.of("googlebooks", "openlibrary", "crossref"), available);

        assertEquals(3, result.size());
        assertSame(googleMock, result.get(0));
        assertSame(openLibMock, result.get(1));
        assertSame(crossRefMock, result.get(2));
    }
}
