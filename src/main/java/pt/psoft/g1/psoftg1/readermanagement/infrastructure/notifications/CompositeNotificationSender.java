package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CompositeNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(CompositeNotificationSender.class);

    private final List<NotificationSender> senders;

    public CompositeNotificationSender(List<NotificationSender> senders) {
        if (senders == null) {
            this.senders = Collections.emptyList();
        } else {
            this.senders = senders.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    @Override
    public void send(NotificationRequest request) {
        Objects.requireNonNull(request, "NotificationRequest must not be null");

        if (senders.isEmpty()) {
            log.debug("[COMPOSITE] No active notification channels configured; skipping dispatch for reader '{}'.",
                    request.readerNumber());
            return;
        }

        log.info("[COMPOSITE] Dispatching notification for reader '{}' (event: '{}') across {} active channel(s).",
                request.readerNumber(), request.eventType(), senders.size());

        for (NotificationSender sender : senders) {
            try {
                sender.send(request);
            } catch (Exception ex) {
                log.error("[COMPOSITE] Notification channel '{}' failed unexpectedly for reader '{}': {}",
                        sender.getChannelName(), request.readerNumber(), ex.getMessage(), ex);
            }
        }
    }

    @Override
    public String getChannelName() {
        if (senders.isEmpty()) {
            return "composite(none)";
        }
        String channels = senders.stream()
                .map(NotificationSender::getChannelName)
                .collect(Collectors.joining(","));
        return "composite(" + channels + ")";
    }

    public List<NotificationSender> getSenders() {
        return Collections.unmodifiableList(senders);
    }

    public int getActiveChannelCount() {
        return senders.size();
    }
}
