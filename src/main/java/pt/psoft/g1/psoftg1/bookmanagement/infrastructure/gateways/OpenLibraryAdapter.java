package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.gateways;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookDetailsDTO;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookInfoProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Infrastructure Adapter for retrieving bibliographic information from the Open Library REST API
 * ({@code GET /api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data}).
 *
 * <p>Translates Open Library's key-indexed JSON model ({@code ISBN:{isbn}}) and nested object arrays
 * ({@code authors[].name}, {@code subjects[].name}, {@code publishers[].name}, {@code notes}/{@code excerpts})
 * into the internal canonical {@link BookDetailsDTO}.</p>
 */
@Component("openlibrary")
public class OpenLibraryAdapter implements BookInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenLibraryAdapter.class);
    public static final String PROVIDER_NAME = "openlibrary";

    private final WebClient webClient;
    private final long timeoutMs;

    @Autowired
    public OpenLibraryAdapter(
            @Value("${bibliographic.openlibrary.base-url:https://openlibrary.org}") String baseUrl,
            @Value("${bibliographic.timeout-ms:3000}") long timeoutMs) {
        this(WebClient.builder().baseUrl(baseUrl).build(), timeoutMs);
    }

    public OpenLibraryAdapter(WebClient webClient) {
        this(webClient, 3000L);
    }

    public OpenLibraryAdapter(WebClient webClient, long timeoutMs) {
        this.webClient = Objects.requireNonNull(webClient, "WebClient must not be null");
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000L;
    }

    @Override
    public Optional<BookDetailsDTO> findBookByIsbn(String isbn) {
        String cleanIsbn = sanitizeIsbn(isbn);
        if (cleanIsbn == null) {
            return Optional.empty();
        }

        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/books")
                            .queryParam("bibkeys", "ISBN:" + cleanIsbn)
                            .queryParam("format", "json")
                            .queryParam("jscmd", "data")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            return mapFromJson(cleanIsbn, response);
        } catch (Exception ex) {
            log.warn("Failed to retrieve book info from Open Library for ISBN {}: {}", cleanIsbn, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Maps the Open Library JSON payload into the canonical {@link BookDetailsDTO}.
     */
    public Optional<BookDetailsDTO> mapFromJson(String isbn, JsonNode rootNode) {
        String cleanIsbn = sanitizeIsbn(isbn);
        if (cleanIsbn == null || rootNode == null || !rootNode.isObject() || rootNode.isEmpty()) {
            return Optional.empty();
        }

        JsonNode bookNode = rootNode.get("ISBN:" + cleanIsbn);
        if (bookNode == null) {
            Iterator<JsonNode> elements = rootNode.elements();
            if (elements.hasNext()) {
                bookNode = elements.next();
            }
        }

        if (bookNode == null || !bookNode.isObject()) {
            return Optional.empty();
        }

        String title = getText(bookNode, "title");
        if (title == null) {
            return Optional.empty();
        }

        String description = extractDescription(bookNode);
        List<String> authors = extractNamedObjectArray(bookNode.get("authors"));
        List<String> subjects = extractNamedObjectArray(bookNode.get("subjects"));
        String genre = subjects.isEmpty() ? null : subjects.get(0);
        List<String> publishers = extractNamedObjectArray(bookNode.get("publishers"));
        String publisher = publishers.isEmpty() ? null : publishers.get(0);
        String publishedDate = getText(bookNode, "publish_date");
        String thumbnailUrl = extractCoverUrl(bookNode.get("cover"));

        return Optional.of(new BookDetailsDTO(
                cleanIsbn,
                title,
                description,
                authors,
                genre,
                publisher,
                publishedDate,
                thumbnailUrl,
                PROVIDER_NAME
        ));
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private static String sanitizeIsbn(String isbn) {
        if (isbn == null) {
            return null;
        }
        String cleaned = isbn.replaceAll("[-\\s]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String getText(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        if (field.isTextual()) {
            String text = field.asText().trim();
            return text.isEmpty() ? null : text;
        }
        if (field.isObject() && field.hasNonNull("value")) {
            JsonNode valueNode = field.get("value");
            if (valueNode.isTextual()) {
                String text = valueNode.asText().trim();
                return text.isEmpty() ? null : text;
            }
        }
        return null;
    }

    private static String extractDescription(JsonNode bookNode) {
        String directDescription = getText(bookNode, "description");
        if (directDescription != null) {
            return directDescription;
        }

        String notes = getText(bookNode, "notes");
        if (notes != null) {
            return notes;
        }

        JsonNode excerpts = bookNode.get("excerpts");
        if (excerpts != null && excerpts.isArray() && !excerpts.isEmpty()) {
            JsonNode firstExcerpt = excerpts.get(0);
            String excerptText = getText(firstExcerpt, "text");
            if (excerptText != null) {
                return excerptText;
            }
        }

        return getText(bookNode, "subtitle");
    }

    private static List<String> extractNamedObjectArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode element : arrayNode) {
                if (element == null) {
                    continue;
                }
                if (element.isTextual()) {
                    String text = element.asText().trim();
                    if (!text.isEmpty()) {
                        result.add(text);
                    }
                } else if (element.isObject()) {
                    String name = getText(element, "name");
                    if (name != null) {
                        result.add(name);
                    }
                }
            }
        }
        return result;
    }

    private static String extractCoverUrl(JsonNode coverNode) {
        if (coverNode == null || !coverNode.isObject()) {
            return null;
        }
        String medium = getText(coverNode, "medium");
        if (medium != null) {
            return medium;
        }
        String large = getText(coverNode, "large");
        if (large != null) {
            return large;
        }
        return getText(coverNode, "small");
    }
}

