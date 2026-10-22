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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Functional Opaque-Box and Structural Unit Tests for {@link GoogleBooksAdapter} (SUT = class).
 *
 * <p>Validates deserialization of Google Books hierarchical JSON responses into {@link BookDetailsDTO},
 * handling of partial/missing fields, corrupt responses, and fault isolation.</p>
 */
class GoogleBooksAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GoogleBooksAdapter adapter;

    @BeforeEach
    void setUp() {
        // Default adapter constructed with mock WebClient for mapping tests
        WebClient mockWebClient = mock(WebClient.class);
        adapter = new GoogleBooksAdapter(mockWebClient, 2000L);
    }

    @Test
    @DisplayName("Should extract complete BookDetailsDTO from valid Google Books volumeInfo")
    void testMapFromJson_ValidFullPayload_ExtractsAllFields() throws Exception {
        String json = """
        {
          "kind": "books#volumes",
          "totalItems": 1,
          "items": [
            {
              "id": "abc123xyz",
              "volumeInfo": {
                "title": "Clean Code",
                "authors": [
                  "Robert C. Martin",
                  "Dean Wampler"
                ],
                "publisher": "Prentice Hall",
                "publishedDate": "2008-08-01",
                "description": "Even bad code can function. But if code isn't clean, it can bring a development organization to its knees.",
                "categories": [
                  "Computers / Programming"
                ],
                "imageLinks": {
                  "smallThumbnail": "http://books.google.com/books/content?id=abc&thumbnail=small",
                  "thumbnail": "http://books.google.com/books/content?id=abc&thumbnail=normal"
                }
              }
            }
          ]
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("978-0132350884", rootNode);

        assertTrue(result.isPresent());
        BookDetailsDTO dto = result.get();
        assertEquals("9780132350884", dto.getIsbn());
        assertEquals("Clean Code", dto.getTitle());
        assertEquals(List.of("Robert C. Martin", "Dean Wampler"), dto.getAuthors());
        assertEquals("Prentice Hall", dto.getPublisher());
        assertEquals("2008-08-01", dto.getPublishedDate());
        assertTrue(dto.getDescription().startsWith("Even bad code can function"));
        assertEquals("Computers / Programming", dto.getGenre());
        assertEquals("http://books.google.com/books/content?id=abc&thumbnail=normal", dto.getThumbnailUrl());
        assertEquals("googlebooks", dto.getSourceProvider());
    }

    @Test
    @DisplayName("Should fallback to smallThumbnail when normal thumbnail is missing")
    void testMapFromJson_SmallThumbnailFallback() throws Exception {
        String json = """
        {
          "totalItems": 1,
          "items": [
            {
              "volumeInfo": {
                "title": "Refactoring",
                "imageLinks": {
                  "smallThumbnail": "http://books.google.com/books/small.jpg"
                }
              }
            }
          ]
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780201485677", rootNode);

        assertTrue(result.isPresent());
        assertEquals("http://books.google.com/books/small.jpg", result.get().getThumbnailUrl());
    }

    @Test
    @DisplayName("Should return empty when items array is empty (book not found on Google Books)")
    void testMapFromJson_NoItems_ReturnsEmpty() throws Exception {
        String json = """
        {
          "kind": "books#volumes",
          "totalItems": 0
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780000000000", rootNode);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty when title is missing in volumeInfo")
    void testMapFromJson_MissingTitle_ReturnsEmpty() throws Exception {
        String json = """
        {
          "totalItems": 1,
          "items": [
            {
              "volumeInfo": {
                "publisher": "Unknown"
              }
            }
          ]
        }
        """;

        JsonNode rootNode = objectMapper.readTree(json);

        Optional<BookDetailsDTO> result = adapter.mapFromJson("9780000000000", rootNode);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty for null, empty or corrupt input")
    void testMapFromJson_CorruptOrNullInput_ReturnsEmpty() throws Exception {
        assertTrue(adapter.mapFromJson(null, objectMapper.readTree("{}")).isEmpty());
        assertTrue(adapter.mapFromJson("   ", objectMapper.readTree("{}")).isEmpty());
        assertTrue(adapter.mapFromJson("9780132350884", null).isEmpty());
        assertTrue(adapter.mapFromJson("9780132350884", objectMapper.readTree("[]")).isEmpty());
    }

    @Test
    @DisplayName("findBookByIsbn should return empty when ISBN is null or empty")
    void testFindBookByIsbn_NullOrEmptyIsbn_ReturnsEmpty() {
        assertTrue(adapter.findBookByIsbn(null).isEmpty());
        assertTrue(adapter.findBookByIsbn("").isEmpty());
        assertTrue(adapter.findBookByIsbn("   ").isEmpty());
    }

    @Test
    @DisplayName("Provider name should be 'googlebooks'")
    void testGetProviderName() {
        assertEquals("googlebooks", adapter.getProviderName());
    }
}

