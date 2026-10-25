package pt.psoft.g1.psoftg1.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications.CompositeNotificationSender;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Configuration
@PropertySource(value = { "classpath:config/library.properties" }, ignoreResourceNotFound = true)
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Value("${notification.channels:email,sms,webhook}")
    private List<String> configuredChannels;

    @Bean
    @Primary
    public NotificationSender notificationSender(List<NotificationSender> availableSenders) {
        List<NotificationSender> selected = resolveSenders(configuredChannels, availableSenders);
        log.info("Initialized NotificationConfig at setup time with active channels: {}",
                selected.stream().map(NotificationSender::getChannelName).toList());
        return new CompositeNotificationSender(selected);
    }

    public List<NotificationSender> resolveSenders(List<String> channelTokens,
            List<NotificationSender> availableSenders) {
        List<NotificationSender> selected = new ArrayList<>();
        if (channelTokens == null || channelTokens.isEmpty()) {
            return selected;
        }

        List<NotificationSender> candidates = availableSenders == null ? List.of() : availableSenders;

        for (String rawToken : channelTokens) {
            if (rawToken == null || rawToken.trim().isEmpty()) {
                continue;
            }
            String token = rawToken.trim();
            Optional<NotificationSender> matched = findInAvailable(token, candidates)
                    .or(() -> instantiateByClassName(token));

            matched.ifPresent(sender -> {
                if (!selected.contains(sender)) {
                    selected.add(sender);
                }
            });
        }

        return selected;
    }

    private Optional<NotificationSender> findInAvailable(String token, List<NotificationSender> availableSenders) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return availableSenders.stream()
                .filter(s -> !(s instanceof CompositeNotificationSender))
                .filter(s -> normalized.equals(s.getChannelName().toLowerCase(Locale.ROOT))
                        || normalized.equals(s.getClass().getSimpleName().toLowerCase(Locale.ROOT))
                        || token.equals(s.getClass().getName()))
                .findFirst();
    }

    private Optional<NotificationSender> instantiateByClassName(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (NotificationSender.class.isAssignableFrom(clazz)) {
                NotificationSender instance = (NotificationSender) clazz.getDeclaredConstructor().newInstance();
                return Optional.of(instance);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("Unrecognized reader notification channel '{}' configured at setup time", className);
        }
        return Optional.empty();
    }
}
