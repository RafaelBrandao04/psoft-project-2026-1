package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.gateways;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookDetailsDTO;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional Opaque-Box and Structural Unit Tests for {@link CrossRefAdapter} (SUT = class).
 *
 * <p>Validates deserialization of CrossRef JSON responses (message.items[]) into {@link BookDetailsDTO},
 * handling of author name combinations, XML/JATS abstract tag stripping, date-parts formatting,
 * and graceful fallback on missing or corrupt payloads.</p>
 */
class CrossRefAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CrossRefAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CrossRefAdapter();
    }

    @Test
    @DisplayName("Should extract complete BookDetailsDTO from valid CrossRef message.items payload")
    void testMapFromJson_ValidFullPayload_ExtractsAllFields() throws Exception {
        String json = """
        {
          "status": "ok",
          "message-type": "work-list",
          "message": {
            "total-results": 1,
            "items": [
              {
                "title": [
                  "Clean Code: A Handbook of Agile Software Craftsmanship"
                ],
                "author": [
                  {
                    "given": "Robert C.",
                    "family": "Martin"
                  },
                  {
                    "given": "Dean",
                    "family": "Wampler"
                  }
                ],
                "publisher": "Prentice Hall",
                "published-print": {
                  "date-parts": [
                    [2008, 8, 1]
                  ]
                },
                "subject": [
                  "Computer Science"
                ],
                "abstract": "<jats:p>Even bad code can function. But if code isn't clean, it can bring an organization down.</jats:p>"
              }
            ]
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("978-0132350884", rootNode);

        assertTrue(result.isPresent());
        BookDetailsDTO dto = result.get();
        assertEquals("9780132350884", dto.getIsbn());
        assertEquals("Clean Code: A Handbook of Agile Software Craftsmanship", dto.getTitle());
        assertEquals(List.of("Robert C. Martin", "Dean Wampler"), dto.getAuthors());
        assertEquals("Prentice Hall", dto.getPublisher());
        assertEquals("2008-08-01", dto.getPublishedDate());
        assertEquals("Computer Science", dto.getGenre());
        assertEquals("Even bad code can function. But if code isn't clean, it can bring an organization down.", dto.getDescription());
        assertNull(dto.getThumbnailUrl()); // CrossRef does not provide thumbnails
        assertEquals("crossref", dto.getSourceProvider());
    }

    @Test
    @DisplayName("Should extract author variants (family only, given only, or name only)")
    void testMapFromJson_AuthorVariants() throws Exception {
        String json = """
        {
          "message": {
            "items": [
              {
                "title": ["Refactoring"],
                "author": [
                  {"family": "Fowler"},
                  {"given": "Kent"},
                  {"name": "Anonymous Organization"},
                  "Plain String Author"
                ]
              }
            ]
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780201485677", rootNode);

        assertTrue(result.isPresent());
        assertEquals(List.of("Fowler", "Kent", "Anonymous Organization", "Plain String Author"),
                result.get().getAuthors());
    }

    @Test
    @DisplayName("Should format date from issued node when published-print is absent and handle year-only date-parts")
    void testMapFromJson_IssuedDateWithYearOnly() throws Exception {
        String json = """
        {
          "message": {
            "items": [
              {
                "title": ["Design Patterns"],
                "issued": {
                  "date-parts": [
                    [1994]
                  ]
                }
              }
            ]
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780201633610", rootNode);

        assertTrue(result.isPresent());
        assertEquals("1994", result.get().getPublishedDate());
    }

    @Test
    @DisplayName("Should use description field when abstract is absent")
    void testMapFromJson_FallbackToDescription() throws Exception {
        String json = """
        {
          "message": {
            "items": [
              {
                "title": ["Domain-Driven Design"],
                "description": "Tackling Complexity in the Heart of Software."
              }
            ]
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780321125217", rootNode);

        assertTrue(result.isPresent());
        assertEquals("Tackling Complexity in the Heart of Software.", result.get().getDescription());
    }

    @Test
    @DisplayName("Should return empty Optional when items array is empty")
    void testMapFromJson_EmptyItems_ReturnsEmpty() throws Exception {
        String json = """
        {
          "status": "ok",
          "message": {
            "total-results": 0,
            "items": []
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780132350884", rootNode);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty Optional when title is missing")
    void testMapFromJson_MissingTitle_ReturnsEmpty() throws Exception {
        String json = """
        {
          "message": {
            "items": [
              {
                "publisher": "Unknown"
              }
            ]
          }
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780132350884", rootNode);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty Optional when rootNode is null or corrupt")
    void testMapFromJson_CorruptNode_ReturnsEmpty() {
        assertTrue(adapter.mapFromJson("9780132350884", null).isEmpty());
        assertTrue(adapter.mapFromJson(null, objectMapper.createObjectNode()).isEmpty());
    }

    @Test
    @DisplayName("Should return correct provider name")
    void testGetProviderName() {
        assertEquals("crossref", adapter.getProviderName());
    }
}
