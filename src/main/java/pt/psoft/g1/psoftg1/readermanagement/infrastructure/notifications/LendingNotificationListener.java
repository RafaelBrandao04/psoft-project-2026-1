package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingCreatedEvent;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingOverdueEvent;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingReturnedEvent;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationSender;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Component
public class LendingNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(LendingNotificationListener.class);

    private final NotificationSender notificationSender;
    private final ReaderRepository readerRepository;

    public LendingNotificationListener(NotificationSender notificationSender, ReaderRepository readerRepository) {
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender must not be null");
        this.readerRepository = Objects.requireNonNull(readerRepository, "readerRepository must not be null");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLendingCreated(LendingCreatedEvent event) {
        if (event == null) {
            return;
        }
        log.info("[NOTIFICATION-LISTENER] Handling LendingCreatedEvent for lending '{}', reader '{}'",
                event.lendingNumber(), event.readerNumber());

        Optional<ReaderDetails> readerOpt = readerRepository.findByReaderNumber(event.readerNumber());
        String email = readerOpt.map(r -> r.getReader().getUsername()).orElse(null);
        String phone = readerOpt.map(ReaderDetails::getPhoneNumber).orElse(null);
        String name = readerOpt.map(r -> r.getReader().getName() != null ? r.getReader().getName().toString() : null)
                .orElse("Reader");

        String subject = "Lending Confirmation: " + event.bookTitle();
        String body = String.format(
                "Dear %s, your lending #%s of the book '%s' (ISBN: %s) has been registered. Due date for return: %s.",
                name, event.lendingNumber(), event.bookTitle(), event.isbn(), event.limitDate());

        NotificationRequest request = new NotificationRequest(
                event.readerNumber(),
                email,
                phone,
                name,
                subject,
                body,
                "LENDING_CREATED");

        notificationSender.send(request);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLendingReturned(LendingReturnedEvent event) {
        if (event == null) {
            return;
        }
        log.info("[NOTIFICATION-LISTENER] Handling LendingReturnedEvent for lending '{}', reader '{}'",
                event.lendingNumber(), event.readerNumber());

        Optional<ReaderDetails> readerOpt = readerRepository.findByReaderNumber(event.readerNumber());
        String email = readerOpt.map(r -> r.getReader().getUsername()).orElse(null);
        String phone = readerOpt.map(ReaderDetails::getPhoneNumber).orElse(null);
        String name = readerOpt.map(r -> r.getReader().getName() != null ? r.getReader().getName().toString() : null)
                .orElse("Reader");

        String subject = "Book Return Confirmation: " + event.bookTitle();
        String body;
        if (event.hasFine()) {
            body = String.format(Locale.US,
                    "Dear %s, your lending #%s of '%s' has been returned with %d day(s) delay. Associated fine: %.2f EUR.",
                    name, event.lendingNumber(), event.bookTitle(), event.daysDelayed(),
                    event.fineValueInCents() / 100.0);
        } else {
            body = String.format("Dear %s, your lending #%s of '%s' has been successfully returned on %s. Thank you!",
                    name, event.lendingNumber(), event.bookTitle(), event.returnedDate());
        }

        NotificationRequest request = new NotificationRequest(
                event.readerNumber(),
                email,
                phone,
                name,
                subject,
                body,
                "LENDING_RETURNED");

        notificationSender.send(request);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLendingOverdue(LendingOverdueEvent event) {
        if (event == null) {
            return;
        }
        log.info("[NOTIFICATION-LISTENER] Handling LendingOverdueEvent for lending '{}', reader '{}'",
                event.lendingNumber(), event.readerNumber());

        Optional<ReaderDetails> readerOpt = readerRepository.findByReaderNumber(event.readerNumber());
        String email = readerOpt.map(r -> r.getReader().getUsername()).orElse(null);
        String phone = readerOpt.map(ReaderDetails::getPhoneNumber).orElse(null);
        String name = readerOpt.map(r -> r.getReader().getName() != null ? r.getReader().getName().toString() : null)
                .orElse("Reader");

        String subject = "OVERDUE NOTICE: " + event.bookTitle();
        String body = String.format(Locale.US,
                "Dear %s, lending #%s of '%s' is overdue by %d day(s) (limit date was %s). Current accumulated fine: %.2f EUR. Please return the book as soon as possible.",
                name, event.lendingNumber(), event.bookTitle(), event.daysOverdue(), event.limitDate(),
                event.fineValueInCents() / 100.0);

        NotificationRequest request = new NotificationRequest(
                event.readerNumber(),
                email,
                phone,
                name,
                subject,
                body,
                "LENDING_OVERDUE");

        notificationSender.send(request);
    }
}
