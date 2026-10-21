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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Infrastructure Adapter for retrieving bibliographic information from the Google Books REST API
 * ({@code GET /volumes?q=isbn:{isbn}}).
 *
 * <p>Translates Google Books' hierarchical {@code items[].volumeInfo} JSON model into the
 * internal canonical {@link BookDetailsDTO}.</p>
 */
@Component("googlebooks")
public class GoogleBooksAdapter implements BookInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleBooksAdapter.class);
    public static final String PROVIDER_NAME = "googlebooks";

    private final WebClient webClient;
    private final long timeoutMs;

    @Autowired
    public GoogleBooksAdapter(
            @Value("${bibliographic.googlebooks.base-url:https://www.googleapis.com/books/v1}") String baseUrl,
            @Value("${bibliographic.timeout-ms:3000}") long timeoutMs) {
        this(WebClient.builder().baseUrl(baseUrl).build(), timeoutMs);
    }

    public GoogleBooksAdapter(WebClient webClient) {
        this(webClient, 3000L);
    }

    public GoogleBooksAdapter(WebClient webClient, long timeoutMs) {
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
                            .path("/volumes")
                            .queryParam("q", "isbn:" + cleanIsbn)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            return mapFromJson(cleanIsbn, response);
        } catch (Exception ex) {
            log.warn("Failed to retrieve book info from Google Books for ISBN {}: {}", cleanIsbn, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Maps the Google Books JSON payload into the canonical {@link BookDetailsDTO}.
     */
    public Optional<BookDetailsDTO> mapFromJson(String isbn, JsonNode rootNode) {
        String cleanIsbn = sanitizeIsbn(isbn);
        if (cleanIsbn == null || rootNode == null || !rootNode.isObject()) {
            return Optional.empty();
        }

        JsonNode items = rootNode.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return Optional.empty();
        }

        JsonNode firstItem = items.get(0);
        if (firstItem == null || !firstItem.isObject()) {
            return Optional.empty();
        }

        JsonNode volumeInfo = firstItem.get("volumeInfo");
        if (volumeInfo == null || !volumeInfo.isObject()) {
            return Optional.empty();
        }

        String title = getText(volumeInfo, "title");
        if (title == null) {
            return Optional.empty();
        }

        String description = getText(volumeInfo, "description");
        List<String> authors = extractStringArray(volumeInfo.get("authors"));
        List<String> categories = extractStringArray(volumeInfo.get("categories"));
        String genre = categories.isEmpty() ? null : categories.get(0);
        String publisher = getText(volumeInfo, "publisher");
        String publishedDate = getText(volumeInfo, "publishedDate");
        String thumbnailUrl = extractThumbnail(volumeInfo.get("imageLinks"));

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
        if (!field.isTextual()) {
            return null;
        }
        String text = field.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static List<String> extractStringArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode element : arrayNode) {
                if (element != null && element.isTextual()) {
                    String text = element.asText().trim();
                    if (!text.isEmpty()) {
                        result.add(text);
                    }
                }
            }
        }
        return result;
    }

    private static String extractThumbnail(JsonNode imageLinksNode) {
        if (imageLinksNode == null || !imageLinksNode.isObject()) {
            return null;
        }
        String thumbnail = getText(imageLinksNode, "thumbnail");
        if (thumbnail != null) {
            return thumbnail;
        }
        return getText(imageLinksNode, "smallThumbnail");
    }
}

