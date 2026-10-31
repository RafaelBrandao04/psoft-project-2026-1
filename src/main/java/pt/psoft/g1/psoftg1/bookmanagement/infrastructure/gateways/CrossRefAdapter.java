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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Infrastructure Adapter for retrieving bibliographic information from the CrossRef REST API
 * ({@code GET /works?filter=isbn:{isbn}}).
 *
 * <p>Demonstrates compliance with Requirement 1.3.3.1 (Point 3):
 * <em>"Supporting new bibliographic information sources with limited impact on existing functionality."</em></p>
 *
 * <p>Translates CrossRef's publication JSON schema ({@code message.items[]}) into the internal canonical
 * {@link BookDetailsDTO}, normalizing authors, publisher, published date, subjects (genre), and abstract.</p>
 */
@Component("crossref")
public class CrossRefAdapter implements BookInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(CrossRefAdapter.class);
    public static final String PROVIDER_NAME = "crossref";

    private final WebClient webClient;
    private final long timeoutMs;

    @Autowired
    public CrossRefAdapter(
            @Value("${bibliographic.crossref.base-url:https://api.crossref.org}") String baseUrl,
            @Value("${bibliographic.timeout-ms:3000}") long timeoutMs) {
        this(WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "LibraryManagementSystem/1.0 (mailto:admin@library.psoft.pt)")
                .build(), timeoutMs);
    }

    public CrossRefAdapter() {
        this("https://api.crossref.org", 3000L);
    }

    public CrossRefAdapter(WebClient webClient) {
        this(webClient, 3000L);
    }

    public CrossRefAdapter(WebClient webClient, long timeoutMs) {
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
                            .path("/works")
                            .queryParam("filter", "isbn:" + cleanIsbn)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            return mapFromJson(cleanIsbn, response);
        } catch (Exception ex) {
            log.warn("Failed to retrieve book info from CrossRef for ISBN {}: {}", cleanIsbn, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Maps the CrossRef JSON payload into the canonical {@link BookDetailsDTO}.
     */
    public Optional<BookDetailsDTO> mapFromJson(String isbn, JsonNode rootNode) {
        String cleanIsbn = sanitizeIsbn(isbn);
        if (cleanIsbn == null || rootNode == null || !rootNode.isObject()) {
            return Optional.empty();
        }

        JsonNode messageNode = rootNode.get("message");
        if (messageNode == null || !messageNode.isObject()) {
            return Optional.empty();
        }

        JsonNode items = messageNode.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return Optional.empty();
        }

        JsonNode firstItem = items.get(0);
        if (firstItem == null || !firstItem.isObject()) {
            return Optional.empty();
        }

        String title = extractTitle(firstItem);
        if (title == null) {
            return Optional.empty();
        }

        String description = extractDescription(firstItem);
        List<String> authors = extractAuthors(firstItem);
        String genre = extractGenre(firstItem);
        String publisher = getText(firstItem, "publisher");
        String publishedDate = extractPublishedDate(firstItem);
        String thumbnailUrl = null; // CrossRef does not host cover images

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

    private static String extractTitle(JsonNode itemNode) {
        if (itemNode == null) {
            return null;
        }
        JsonNode titleNode = itemNode.get("title");
        if (titleNode != null) {
            if (titleNode.isArray() && !titleNode.isEmpty()) {
                JsonNode first = titleNode.get(0);
                if (first != null && first.isTextual()) {
                    String text = first.asText().trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            } else if (titleNode.isTextual()) {
                String text = titleNode.asText().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static List<String> extractAuthors(JsonNode itemNode) {
        List<String> authors = new ArrayList<>();
        if (itemNode == null || !itemNode.hasNonNull("author")) {
            return authors;
        }
        JsonNode authorArray = itemNode.get("author");
        if (authorArray.isArray()) {
            for (JsonNode author : authorArray) {
                if (author != null && author.isObject()) {
                    String given = getText(author, "given");
                    String family = getText(author, "family");
                    if (given != null && family != null) {
                        authors.add(given + " " + family);
                    } else if (family != null) {
                        authors.add(family);
                    } else if (given != null) {
                        authors.add(given);
                    } else {
                        String name = getText(author, "name");
                        if (name != null) {
                            authors.add(name);
                        }
                    }
                } else if (author != null && author.isTextual()) {
                    String text = author.asText().trim();
                    if (!text.isEmpty()) {
                        authors.add(text);
                    }
                }
            }
        }
        return authors;
    }

    private static String extractGenre(JsonNode itemNode) {
        if (itemNode == null || !itemNode.hasNonNull("subject")) {
            return null;
        }
        JsonNode subjectNode = itemNode.get("subject");
        if (subjectNode.isArray() && !subjectNode.isEmpty()) {
            JsonNode first = subjectNode.get(0);
            if (first != null && first.isTextual()) {
                String text = first.asText().trim();
                return text.isEmpty() ? null : text;
            }
        }
        return null;
    }

    private static String extractDescription(JsonNode itemNode) {
        if (itemNode == null) {
            return null;
        }
        String rawAbstract = getText(itemNode, "abstract");
        if (rawAbstract != null) {
            // Strip XML/JATS/HTML tags like <jats:p>, </jats:p>, etc.
            String stripped = rawAbstract.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            if (!stripped.isEmpty()) {
                return stripped;
            }
        }
        return getText(itemNode, "description");
    }

    private static String extractPublishedDate(JsonNode itemNode) {
        if (itemNode == null) {
            return null;
        }

        String[] dateFields = {"published-print", "issued", "published-online", "created"};
        for (String fieldName : dateFields) {
            JsonNode dateNode = itemNode.get(fieldName);
            if (dateNode != null && dateNode.isObject()) {
                JsonNode dateParts = dateNode.get("date-parts");
                if (dateParts != null && dateParts.isArray() && !dateParts.isEmpty()) {
                    JsonNode firstParts = dateParts.get(0);
                    if (firstParts != null && firstParts.isArray() && !firstParts.isEmpty()) {
                        int year = firstParts.get(0).asInt();
                        if (firstParts.size() >= 3) {
                            int month = firstParts.get(1).asInt();
                            int day = firstParts.get(2).asInt();
                            return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
                        } else if (firstParts.size() == 2) {
                            int month = firstParts.get(1).asInt();
                            return String.format(Locale.ROOT, "%04d-%02d", year, month);
                        } else {
                            return String.valueOf(year);
                        }
                    }
                }
            }
        }
        return null;
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
}
