package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.gateways;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookDetailsDTO;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Functional Opaque-Box and Structural Unit Tests for {@link OpenLibraryAdapter} (SUT = class).
 *
 * <p>Validates deserialization of Open Library key-indexed JSON responses (with nested
 * author/subject/publisher object arrays, multiple description variants, and cover links)
 * into the canonical {@link BookDetailsDTO}.</p>
 */
class OpenLibraryAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenLibraryAdapter adapter;

    @BeforeEach
    void setUp() {
        WebClient mockWebClient = mock(WebClient.class);
        adapter = new OpenLibraryAdapter(mockWebClient, 2000L);
    }

    @Test
    @DisplayName("Should extract complete BookDetailsDTO from valid Open Library jscmd=data payload")
    void testMapFromJson_ValidFullPayload_ExtractsAllFields() throws Exception {
        String json = """
        {
          "ISBN:9780132350884": {
            "title": "Clean Code",
            "subtitle": "A Handbook of Agile Software Craftsmanship",
            "authors": [
              {
                "url": "https://openlibrary.org/authors/OL21591A/Robert_C._Martin",
                "name": "Robert C. Martin"
              }
            ],
            "publishers": [
              {
                "name": "Prentice Hall"
              }
            ],
            "publish_date": "August 2008",
            "subjects": [
              {
                "name": "Computer software",
                "url": "https://openlibrary.org/subjects/computer_software"
              },
              {
                "name": "Software development"
              }
            ],
            "notes": "Even bad code can function. But if code isn't clean, it can bring a development organization to its knees.",
            "cover": {
              "small": "https://covers.openlibrary.org/b/id/123-S.jpg",
              "medium": "https://covers.openlibrary.org/b/id/123-M.jpg",
              "large": "https://covers.openlibrary.org/b/id/123-L.jpg"
            }
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("978-0132350884", rootNode);

        assertTrue(result.isPresent());
        BookDetailsDTO dto = result.get();
        assertEquals("9780132350884", dto.getIsbn());
        assertEquals("Clean Code", dto.getTitle());
        assertEquals(List.of("Robert C. Martin"), dto.getAuthors());
        assertEquals("Prentice Hall", dto.getPublisher());
        assertEquals("August 2008", dto.getPublishedDate());
        assertEquals("Computer software", dto.getGenre());
        assertTrue(dto.getDescription().contains("Even bad code can function"));
        assertEquals("https://covers.openlibrary.org/b/id/123-M.jpg", dto.getThumbnailUrl());
        assertEquals("openlibrary", dto.getSourceProvider());
    }

    @Test
    @DisplayName("Should extract description from 'description' field if present")
    void testMapFromJson_DirectDescription() throws Exception {
        String json = """
        {
          "ISBN:9780132350884": {
            "title": "Clean Code",
            "description": "Direct description text."
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);
        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780132350884", rootNode);

        assertTrue(result.isPresent());
        assertEquals("Direct description text.", result.get().getDescription());
    }

    @Test
    @DisplayName("Should extract description from 'notes' object with value property")
    void testMapFromJson_NotesObjectValue() throws Exception {
        String json = """
        {
          "ISBN:9780132350884": {
            "title": "Clean Code",
            "notes": {
              "value": "Notes from object value."
            }
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);
        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780132350884", rootNode);

        assertTrue(result.isPresent());
        assertEquals("Notes from object value.", result.get().getDescription());
    }

    @Test
    @DisplayName("Should extract description from excerpts when notes and description are missing")
    void testMapFromJson_ExcerptFallback() throws Exception {
        String json = """
        {
          "ISBN:9780132350884": {
            "title": "Clean Code",
            "excerpts": [
              {
                "text": "Excerpt text from book."
              }
            ]
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);
        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780132350884", rootNode);

        assertTrue(result.isPresent());
        assertEquals("Excerpt text from book.", result.get().getDescription());
    }

    @Test
    @DisplayName("Should extract description from subtitle when all other description fields are absent")
    void testMapFromJson_SubtitleFallback() throws Exception {
        String json = """
        {
          "ISBN:9780132350884": {
            "title": "Clean Code",
            "subtitle": "A Handbook of Agile Craftsmanship"
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);
        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780132350884", rootNode);

        assertTrue(result.isPresent());
        assertEquals("A Handbook of Agile Craftsmanship", result.get().getDescription());
    }

    @Test
    @DisplayName("Should return empty when Open Library returns empty object (book not found)")
    void testMapFromJson_EmptyObject_ReturnsEmpty() throws Exception {
        String json = "{}";

        JsonNode rootNode = objectMapper.readTree(json);
        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780000000000", rootNode);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty when title is missing")
    void testMapFromJson_MissingTitle_ReturnsEmpty() throws Exception {
        String json = """
        {
          "ISBN:9780132350884": {
            "publish_date": "2008"
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);
        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780132350884", rootNode);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty for null or corrupt input")
    void testMapFromJson_NullOrCorrupt() throws Exception {
        assertTrue(adapter.mapFromJson(null, objectMapper.readTree("{}")).isEmpty());
        assertTrue(adapter.mapFromJson("  ", objectMapper.readTree("{}")).isEmpty());
        assertTrue(adapter.mapFromJson("9780132350884", null).isEmpty());
    }

    @Test
    @DisplayName("findBookByIsbn should return empty for null or blank ISBN")
    void testFindBookByIsbn_NullOrBlank() {
        assertTrue(adapter.findBookByIsbn(null).isEmpty());
        assertTrue(adapter.findBookByIsbn("").isEmpty());
        assertTrue(adapter.findBookByIsbn("   ").isEmpty());
    }

    @Test
    @DisplayName("Provider name should be 'openlibrary'")
    void testGetProviderName() {
        assertEquals("openlibrary", adapter.getProviderName());
    }
}

