package pt.psoft.g1.psoftg1.lendingmanagement.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.lendingmanagement.repositories.LendingRepository;
import pt.psoft.g1.psoftg1.lendingmanagement.services.CreateLendingRequest;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
import pt.psoft.g1.psoftg1.usermanagement.model.Reader;
import pt.psoft.g1.psoftg1.usermanagement.repositories.UserRepository;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Functional Opaque-Box System Acceptance Tests for Lending Policies (Criterion C3.5).
 *
 * <p>SUT = System (HTTP REST interface via {@link MockMvc} through Controller, Service,
 * {@code LendingPolicyResolver}, {@code StandardLendingPolicy}, {@code AdaptiveLendingPolicy},
 * Repositories, and H2 Database).</p>
 *
 * <p>Scenarios tested:
 * <ul>
 *   <li>1. Default {@code StandardLendingPolicy} for a reader without qualifying history:
 *          15-day lending period, up to 3 active lendings, 4th lending rejected (403 Forbidden),
 *          and renewal up to {@code MaxRenewals = 2} (each extending {@code limitDate} by 15 days),
 *          3rd renewal rejected (403 Forbidden).</li>
 *   <li>2. Transition to {@code AdaptiveLendingPolicy} when reader has {@code >= 3} completed lendings
 *          in the last 6 months with {@code >= 80%} on-time ratio and no overdue lendings:
 *          1st active lending gets {@code LongPeriod} (30 days),
 *          2nd active lending gets {@code StandardPeriod} (15 days),
 *          3rd and 4th active lendings get {@code ShortPeriod} (7 days).</li>
 *   <li>3. Dynamic policy re-evaluation on history/overdue change (Section 4.3):
 *          when a previously adaptive reader acquires an overdue lending or drops below the on-time
 *          ratio, new lendings and renewals are blocked or re-evaluated accordingly.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class LendingPolicyAcceptanceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LendingRepository lendingRepository;

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

    private Author author;
    private Genre genre;
    private Book book;
    private Reader standardUser;
    private ReaderDetails standardReader;
    private Reader adaptiveUser;
    private ReaderDetails adaptiveReader;

    @BeforeEach
    void setUp() {
        author = authorRepository.save(new Author("José Saramago", "Prémio Nobel da Literatura", null));
        genre = genreRepository.save(new Genre("Ficção"));
        book = bookRepository.save(new Book(
                "9782826012092",
                "Ensaio sobre a Cegueira",
                "Romance alegórico sobre uma epidemia de cegueira branca",
                genre,
                List.of(author),
                null));

        standardUser = userRepository.save(Reader.newReader("standard.e2e@example.com", "Password123!", "Leitor Standard"));
        standardReader = readerRepository.save(new ReaderDetails(
                901, standardUser, "1995-04-10", "919111222", true, true, true, null, null));

        adaptiveUser = userRepository.save(Reader.newReader("adaptive.e2e@example.com", "Password123!", "Leitora Adaptive"));
        adaptiveReader = readerRepository.save(new ReaderDetails(
                902, adaptiveUser, "1992-08-20", "919333444", true, true, true, null, null));

        // Seed 3 completed on-time lendings in the last 2 months for adaptiveReader (100% >= 80% on-time ratio)
        LocalDate twoMonthsAgo = LocalDate.now().minusMonths(2);
        for (int i = 1; i <= 3; i++) {
            Lending completedOnTime = Lending.newBootstrappingLending(
                    book,
                    adaptiveReader,
                    LocalDate.now().getYear(),
                    800 + i,
                    twoMonthsAgo.plusDays(i),
                    twoMonthsAgo.plusDays(i + 10), // returned in 10 days (limit was 15) -> on time
                    15,
                    200
            );
            lendingRepository.save(completedOnTime);
        }
    }

    @AfterEach
    void tearDown() {
        // @Transactional rolls back automatically after each test
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("E2E Standard Policy: 15-day period, MaxActiveLendings=3, MaxRenewals=2 via HTTP REST")
    void testStandardPolicyLendingsAndRenewalsEndToEnd() throws Exception {
        String readerNum = standardReader.getReaderNumber();
        CreateLendingRequest createReq = new CreateLendingRequest(book.getIsbn(), readerNum);
        LocalDate expectedStandardLimit = LocalDate.now().plusDays(15);

        // 1st lending -> 201 Created, limitDate = today + 15 days
        String responseJson = mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitDate").value(expectedStandardLimit.toString()))
                .andReturn().getResponse().getContentAsString();

        String lendingNumber1 = objectMapper.readTree(responseJson).get("lendingNumber").asText();

        // Renew 1st lending (Renewal #1) -> 200 OK, limitDate extended by 15 days (today + 30 days)
        mockMvc.perform(post("/api/lendings/" + lendingNumber1 + "/renew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitDate").value(expectedStandardLimit.plusDays(15).toString()));

        // Renew 1st lending again (Renewal #2 == MaxRenewals) -> 200 OK, limitDate extended by 15 days (today + 45 days)
        mockMvc.perform(post("/api/lendings/" + lendingNumber1 + "/renew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitDate").value(expectedStandardLimit.plusDays(30).toString()));

        // Renew 1st lending a 3rd time (exceeds MaxRenewals=2) -> 403 Forbidden
        mockMvc.perform(post("/api/lendings/" + lendingNumber1 + "/renew"))
                .andExpect(status().isForbidden());

        // 2nd and 3rd active lendings -> 201 Created
        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // 4th active lending -> exceeds Standard MaxActiveLendings (3) -> 403 Forbidden
        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("E2E Adaptive Policy: Reader with controlled history gets Long (30d), Standard (15d), and Short (7d) periods")
    void testAdaptivePolicyThresholdsEndToEnd() throws Exception {
        String readerNum = adaptiveReader.getReaderNumber();
        CreateLendingRequest createReq = new CreateLendingRequest(book.getIsbn(), readerNum);

        // 1st active lending (0 currently active < FirstThreshold=1) -> LongPeriod = 30 days
        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitDate").value(LocalDate.now().plusDays(30).toString()));

        // 2nd active lending (1 currently active: FirstThreshold=1 <= N < SecondThreshold=2) -> StandardPeriod = 15 days
        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitDate").value(LocalDate.now().plusDays(15).toString()));

        // 3rd active lending (2 currently active >= SecondThreshold=2) -> ShortPeriod = 7 days
        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitDate").value(LocalDate.now().plusDays(7).toString()));

        // 4th active lending (3 currently active < MaxActiveLendings=5, allowed under Adaptive!) -> ShortPeriod = 7 days
        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitDate").value(LocalDate.now().plusDays(7).toString()));
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("E2E Section 4.3 Re-evaluation: Reader transitions from Standard to Adaptive and back when history changes")
    void testPolicyReEvaluationWhenReaderHistoryChangesEndToEnd() throws Exception {
        String readerNum = standardReader.getReaderNumber();
        CreateLendingRequest createReq = new CreateLendingRequest(book.getIsbn(), readerNum);

        // Step 1: Initially standardReader has 0 completed lendings -> Standard Policy (15 days)
        String firstLendingJson = mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitDate").value(LocalDate.now().plusDays(15).toString()))
                .andReturn().getResponse().getContentAsString();

        String firstLendingNum = objectMapper.readTree(firstLendingJson).get("lendingNumber").asText();

        // Step 2: Reader completes 3 on-time lendings within the history period
        LocalDate oneMonthAgo = LocalDate.now().minusMonths(1);
        for (int i = 1; i <= 3; i++) {
            lendingRepository.save(Lending.newBootstrappingLending(
                    book, standardReader, LocalDate.now().getYear(), 850 + i,
                    oneMonthAgo.plusDays(i), oneMonthAgo.plusDays(i + 5), 15, 200));
        }

        // Step 3: Renewing firstLending now re-evaluates policy -> selects Adaptive Policy!
        // Since standardReader has only 1 active lending (0 other active < FirstThreshold=1), renewal extends by LongPeriod (30 days)
        LocalDate expectedAfterAdaptiveRenewal = LocalDate.now().plusDays(15).plusDays(30);
        mockMvc.perform(post("/api/lendings/" + firstLendingNum + "/renew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitDate").value(expectedAfterAdaptiveRenewal.toString()));

        // Step 4: Reader subsequently completes 2 late lendings (now 3 on-time out of 5 = 60% < 80% MinOnTimeRatio)
        for (int i = 1; i <= 2; i++) {
            lendingRepository.save(Lending.newBootstrappingLending(
                    book, standardReader, LocalDate.now().getYear(), 860 + i,
                    oneMonthAgo.plusDays(i), oneMonthAgo.plusDays(i + 25), 15, 200));
        }

        // Step 5: Next lending request re-evaluates policy -> drops back to Standard Policy (15 days)
        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitDate").value(LocalDate.now().plusDays(15).toString()));
    }
}

