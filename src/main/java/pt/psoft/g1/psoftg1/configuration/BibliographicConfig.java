package pt.psoft.g1.psoftg1.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.gateways.CompositeBookInfoProvider;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookInfoProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Setup-time configuration for selecting and composing external bibliographic information sources.
 *
 * <p>Reads the {@code bibliographic.providers} property (from {@code config/library.properties}
 * or {@code application.properties}) at application startup and assembles a {@link CompositeBookInfoProvider}
 * containing the selected {@link BookInfoProvider} implementations in the exact priority order specified.</p>
 *
 * <p>Supports both logical provider identifiers (e.g., {@code googlebooks}, {@code openlibrary}) and
 * fully-qualified class names (dynamic reflective instantiation via {@link Class#forName(String)}),
 * ensuring strict compliance with the Open-Closed Principle (OCP).</p>
 */
@Configuration
@PropertySource({"classpath:config/library.properties"})
public class BibliographicConfig {

    private static final Logger log = LoggerFactory.getLogger(BibliographicConfig.class);

    @Value("${bibliographic.providers:googlebooks,openlibrary}")
    private List<String> configuredProviders;

    @Bean
    @Primary
    public BookInfoProvider bookInfoProvider(List<BookInfoProvider> availableProviders) {
        List<BookInfoProvider> selected = resolveProviders(configuredProviders, availableProviders);
        log.info("Initialized BibliographicConfig at setup time with active providers: {}",
                selected.stream().map(BookInfoProvider::getProviderName).toList());
        return new CompositeBookInfoProvider(selected);
    }

    public List<BookInfoProvider> resolveProviders(
            List<String> providerTokens,
            List<BookInfoProvider> availableProviders) {
        List<BookInfoProvider> selected = new ArrayList<>();
        if (providerTokens == null || providerTokens.isEmpty()) {
            return selected;
        }

        List<BookInfoProvider> candidates = availableProviders == null ? List.of() : availableProviders;

        for (String rawToken : providerTokens) {
            if (rawToken == null || rawToken.trim().isEmpty()) {
                continue;
            }
            String token = rawToken.trim();
            Optional<BookInfoProvider> matched = findInAvailable(token, candidates)
                    .or(() -> instantiateByClassName(token));

            matched.ifPresent(provider -> {
                if (!selected.contains(provider)) {
                    selected.add(provider);
                }
            });
        }

        return selected;
    }

    private Optional<BookInfoProvider> findInAvailable(String token, List<BookInfoProvider> availableProviders) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return availableProviders.stream()
                .filter(p -> !(p instanceof CompositeBookInfoProvider))
                .filter(p -> normalized.equals(p.getProviderName().toLowerCase(Locale.ROOT))
                        || normalized.equals(p.getClass().getSimpleName().toLowerCase(Locale.ROOT))
                        || token.equals(p.getClass().getName()))
                .findFirst();
    }

    private Optional<BookInfoProvider> instantiateByClassName(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (BookInfoProvider.class.isAssignableFrom(clazz)) {
                BookInfoProvider instance = (BookInfoProvider) clazz.getDeclaredConstructor().newInstance();
                return Optional.of(instance);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("Unrecognized bibliographic provider '{}' configured at setup time", className);
        }
        return Optional.empty();
    }
}

