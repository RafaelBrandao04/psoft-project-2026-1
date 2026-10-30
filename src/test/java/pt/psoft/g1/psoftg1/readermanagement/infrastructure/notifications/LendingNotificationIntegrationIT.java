package pt.psoft.g1.psoftg1.readermanagement.infrastructure.notifications;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.lendingmanagement.repositories.FineRepository;
import pt.psoft.g1.psoftg1.lendingmanagement.repositories.LendingRepository;
import pt.psoft.g1.psoftg1.lendingmanagement.services.LendingService;
import pt.psoft.g1.psoftg1.lendingmanagement.services.SetLendingReturnedRequest;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
import pt.psoft.g1.psoftg1.readermanagement.services.NotificationRequest;
import pt.psoft.g1.psoftg1.usermanagement.model.Reader;
import pt.psoft.g1.psoftg1.usermanagement.repositories.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Functional Opaque-Box Integration Test (SUT = Controller + Service + Event Bus + Gateways).
 *
 * <p>Requirement 1.3.3.4.4 & Technical Memo TM-02 Verification:
 * Validates that creating/returning lendings through the REST API correctly triggers
 * asynchronous domain events and multichannel notifications, and that external delivery
 * failures (e.g. simulated Webhook breakdown) are isolated without causing transaction rollback.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
public class LendingNotificationIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LendingService lendingService;

    @Autowired
    private LendingRepository lendingRepository;


    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private ReaderRepository readerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private EmailNotificationSender emailSender;

    @Autowired
    private SmsNotificationSender smsSender;

    @SpyBean
    private WebhookNotificationSender webhookSender;

    private Book testBook;
    private Genre testGenre;
    private Author testAuthor;
    private Reader testReader;
    private ReaderDetails testReaderDetails;
    private String readerNumberStr;

    @BeforeEach
    void setUp() {
        emailSender.resetSentCount();
        smsSender.resetSentCount();
        webhookSender.resetMetrics();

        readerNumberStr = LocalDate.now().getYear() + "/888";

        // Clean up any stale data from previous runs
        cleanUpData();

        testAuthor = authorRepository.save(new Author("Martin Fowler IT", "Renowned software author", null));
        testGenre = genreRepository.findByString("SoftwareNotif")
                .orElseGet(() -> genreRepository.save(new Genre("SoftwareNotif")));
        testBook = bookRepository.findByIsbn("9780134757599")
                .orElseGet(() -> bookRepository.save(new Book("9780134757599", "Refactoring 2nd Edition", "Improving Code Design",
                        testGenre, List.of(testAuthor), null)));

        testReader = Reader.newReader("reader.notif.it@example.com", "Password123!", "Alice Walker");
        userRepository.save(testReader);

        testReaderDetails = new ReaderDetails(888, testReader, "1995-05-20", "912345678", true, true, true, null, null);
        readerRepository.save(testReaderDetails);
    }

    @AfterEach
    void tearDown() {
        cleanUpData();
    }

    private void cleanUpData() {
        if (readerNumberStr != null && testBook != null) {
            try {
                List<Lending> lendings = lendingRepository.listByReaderNumberAndIsbn(readerNumberStr, testBook.getIsbn());
                for (Lending l : lendings) {
                    lendingRepository.delete(l);
                }
            } catch (Exception ignored) {}
        }
        if (readerNumberStr != null) {
            try {
                for (Lending l : lendingRepository.listOutstandingByReaderNumber(readerNumberStr)) {
                    lendingRepository.delete(l);
                }
            } catch (Exception ignored) {}
        }
        if (testReaderDetails != null) {
            try {
                readerRepository.delete(testReaderDetails);
            } catch (Exception ignored) {}
            testReaderDetails = null;
        } else if (readerNumberStr != null) {
            readerRepository.findByReaderNumber(readerNumberStr).ifPresent(rd -> {
                try {
                    readerRepository.delete(rd);
                } catch (Exception ignored) {}
            });
        }
        if (testReader != null) {
            try {
                userRepository.delete(testReader);
            } catch (Exception ignored) {}
            testReader = null;
        } else {
            userRepository.findByUsername("reader.notif.it@example.com").ifPresent(u -> {
                try {
                    userRepository.delete(u);
                } catch (Exception ignored) {}
            });
        }
        if (testBook != null) {
            try {
                bookRepository.delete(testBook);
            } catch (Exception ignored) {}
            testBook = null;
        }
        if (testGenre != null) {
            try {
                genreRepository.delete(testGenre);
            } catch (Exception ignored) {}
            testGenre = null;
        }
        if (testAuthor != null) {
            try {
                authorRepository.delete(testAuthor);
            } catch (Exception ignored) {}
            testAuthor = null;
        }
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("End-to-End: Lending creation via REST API triggers domain events and multichannel notifications")
    void testLendingCreation_TriggersMultichannelNotifications() throws Exception {
        // Isolate webhook channel from external network dependency
        doNothing().when(webhookSender).send(any(NotificationRequest.class));

        String requestJson = String.format("""
                {
                    "isbn": "%s",
                    "readerNumber": "%s"
                }
                """, testBook.getIsbn(), readerNumberStr);

        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lendingNumber").exists());

        // Verifies database persistence
        List<Lending> lendings = lendingRepository.listByReaderNumberAndIsbn(readerNumberStr, testBook.getIsbn());
        assertFalse(lendings.isEmpty());

        // Verifies that all 3 active channels (Email, SMS, Webhook) were notified
        assertTrue(emailSender.getSentCount() >= 1, "EmailNotificationSender should have dispatched at least 1 message");
        assertTrue(smsSender.getSentCount() >= 1, "SmsNotificationSender should have dispatched at least 1 SMS");
        verify(webhookSender, atLeastOnce()).send(any(NotificationRequest.class));
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("ASR06 Fault Isolation: Webhook failure does NOT rollback lending persistence or block Email/SMS")
    void testFaultIsolation_WebhookFailureDoesNotRollbackLending() throws Exception {
        // Simulate external webhook failure (e.g. server down / timeout)
        doThrow(new RuntimeException("Simulated 500 Internal Server Error from Webhook server"))
                .when(webhookSender).send(any(NotificationRequest.class));

        String requestJson = String.format("""
                {
                    "isbn": "%s",
                    "readerNumber": "%s"
                }
                """, testBook.getIsbn(), readerNumberStr);

        // Even with Webhook failing, HTTP status MUST be 201 Created
        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lendingNumber").exists());

        // Lending MUST be persisted in H2 database (Fault Isolation guarantees zero rollback)
        List<Lending> lendings = lendingRepository.listByReaderNumberAndIsbn(readerNumberStr, testBook.getIsbn());
        assertFalse(lendings.isEmpty(), "Lending MUST be successfully committed to DB despite webhook failure");

        // Remaining active channels (Email and SMS) MUST still execute
        assertTrue(emailSender.getSentCount() >= 1, "Email channel should still execute");
        assertTrue(smsSender.getSentCount() >= 1, "SMS channel should still execute");
    }

    @Test
    @DisplayName("End-to-End: Lending return triggers return notification event")
    void testLendingReturn_TriggersReturnNotification() {
        // 1. Create a lending first
        int seq = lendingRepository.getCountFromCurrentYear() + 1;
        Lending lending = new Lending(testBook, testReaderDetails, seq, 14, 200);
        lendingRepository.save(lending);

        emailSender.resetSentCount();
        smsSender.resetSentCount();
        doNothing().when(webhookSender).send(any(NotificationRequest.class));

        // 2. Return lending via lendingService
        SetLendingReturnedRequest returnRequest = new SetLendingReturnedRequest("Excellent book, highly recommended!");
        Lending returned = lendingService.setReturned(lending.getLendingNumber(), returnRequest, 0L);
        assertNotNull(returned);
        assertNotNull(returned.getReturnedDate());

        // Verifies return notification was dispatched to all active channels
        assertTrue(emailSender.getSentCount() >= 1, "Email should be dispatched upon return");
        assertTrue(smsSender.getSentCount() >= 1, "SMS should be dispatched upon return");
        verify(webhookSender, atLeastOnce()).send(any(NotificationRequest.class));

        // Verify entity state
        Optional<Lending> updated = lendingRepository.findByLendingNumber(lending.getLendingNumber());
        assertTrue(updated.isPresent());
        assertNotNull(updated.get().getReturnedDate());
    }
}
