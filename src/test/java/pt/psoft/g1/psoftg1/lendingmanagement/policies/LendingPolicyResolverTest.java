package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.configuration.LendingPolicyProperties;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.usermanagement.model.Reader;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Structural (White-Box) Unit Tests for {@link LendingPolicyResolver} (Criterion C3.2).
 *
 * <p>Covers all decision branches and independent condition variations of Section 4
 * (Lending Policy Selection) of Lending Policies Specification v1.0:
 * <ul>
 *   <li>4.1. {@code StandardLendingPolicy} applies by default</li>
 *   <li>4.2. {@code AdaptiveLendingPolicy} applies when during the previous {@code HistoryPeriod (> 0)} months:
 *       <ul>
 *         <li>4.2.1. Completed lendings {@code >= MinCompletedLendings (> 0)}</li>
 *         <li>4.2.2. On-time ratio {@code >= MinOnTimeRatio (0-100%)}</li>
 *         <li>4.2.3. Reader has no currently overdue lending</li>
 *       </ul>
 *   </li>
 *   <li>4.3. Applicable lending policy is re-evaluated whenever a new lending or renewal is requested</li>
 * </ul>
 */
class LendingPolicyResolverTest {

    private LendingPolicyResolver resolver;
    private StandardLendingPolicy standardPolicy;
    private AdaptiveLendingPolicy adaptivePolicy;

    private ReaderDetails reader;
    private Book regularBook;
    private Book referenceBook;

    @BeforeEach
    void setUp() {
        LendingPolicyProperties properties = new LendingPolicyProperties();
        standardPolicy = new StandardLendingPolicy(properties);
        adaptivePolicy = new AdaptiveLendingPolicy(properties);

        // HistoryPeriod = 6 months, MinCompletedLendings = 4, MinOnTimeRatio = 75.0%
        resolver = new LendingPolicyResolver(standardPolicy, adaptivePolicy, 6, 4, 75.0);

        List<Author> authors = List.of(new Author("Autor", "Bio", null));
        regularBook = new Book("9782826012092", "Romance Histórico", "Desc", new Genre("Romance"), authors, null);
        referenceBook = new Book("8175257660", "Dicionário de Referência", "Desc", new Genre("Reference"), authors, null);

        Reader r = Reader.newReader("reader@example.com", "Pass1234!", "Leitor");
        reader = new ReaderDetails(1, r, "1990-01-01", "910000001", true, false, false, null, null);
    }

    private Lending createCompletedLending(int seq, LocalDate startDate, boolean onTime) {
        LocalDate returnedDate = onTime ? startDate.plusDays(10) : startDate.plusDays(25);
        return Lending.newBootstrappingLending(regularBook, reader, 2026, seq, startDate, returnedDate, 15, 200);
    }

    private Lending createActiveLending(int seq, boolean overdue) {
        LocalDate startDate = overdue ? LocalDate.now().minusDays(20) : LocalDate.now().minusDays(5);
        return Lending.newBootstrappingLending(regularBook, reader, 2026, seq, startDate, null, 15, 200);
    }

    @Test
    @DisplayName("Section 4.1: Standard Lending Policy applies by default when reader has no history")
    void testDefaultStandardPolicyWhenNoHistory() {
        LendingPolicy p1 = resolver.resolvePolicy(reader, regularBook, List.of(), null);
        assertEquals(StandardLendingPolicy.POLICY_NAME, p1.getPolicyName());

        LendingPolicy p2 = resolver.resolvePolicy(reader, regularBook, List.of(), List.of());
        assertEquals(StandardLendingPolicy.POLICY_NAME, p2.getPolicyName());

        LendingPolicy p3 = resolver.resolvePolicy(reader, regularBook, List.of());
        assertEquals(StandardLendingPolicy.POLICY_NAME, p3.getPolicyName());
    }

    @Test
    @DisplayName("Section 4.2AllConditionsTrue: Adaptive Lending Policy selected when 4.2.1, 4.2.2, and 4.2.3 all hold")
    void testSelectsAdaptiveWhenAllConditionsSatisfied() {
        LocalDate recent = LocalDate.now().minusMonths(2);
        // 4 completed lendings in last 6 months, 3 on time + 1 late = 75.0% (== MinOnTimeRatio)
        List<Lending> history = List.of(
                createCompletedLending(1, recent, true),
                createCompletedLending(2, recent, true),
                createCompletedLending(3, recent, true),
                createCompletedLending(4, recent, false)
        );
        List<Lending> active = List.of(createActiveLending(5, false));

        LendingPolicy resolved = resolver.resolvePolicy(reader, regularBook, active, history);
        assertEquals(AdaptiveLendingPolicy.POLICY_NAME, resolved.getPolicyName());
    }

    @Test
    @DisplayName("Section 4.2.1 Variation (MinCompletedLendings): Fails when completed lendings < MinCompletedLendings (3 < 4)")
    void testFailsWhenCompletedLendingsBelowMinimum() {
        LocalDate recent = LocalDate.now().minusMonths(1);
        // Only 3 completed lendings (MinCompletedLendings - 1 = 3 < 4), all 100% on time, 0 overdue
        List<Lending> history = List.of(
                createCompletedLending(1, recent, true),
                createCompletedLending(2, recent, true),
                createCompletedLending(3, recent, true)
        );

        LendingPolicy resolved = resolver.resolvePolicy(reader, regularBook, List.of(), history);
        assertEquals(StandardLendingPolicy.POLICY_NAME, resolved.getPolicyName());
    }

    @Test
    @DisplayName("Section 4.2.1 & 4.2 Variation (HistoryPeriod): Lendings outside HistoryPeriod window are excluded")
    void testVaryingHistoryPeriodWindowExcludesOlderLendings() {
        LocalDate within2Months = LocalDate.now().minusMonths(2);
        LocalDate fiveMonthsAgo = LocalDate.now().minusMonths(5);
        LocalDate eightMonthsAgo = LocalDate.now().minusMonths(8); // outside 6-month window

        // 3 recent + 1 eight months ago -> only 3 inside 6-month window (< 4) -> Standard
        List<Lending> historyWithOld = List.of(
                createCompletedLending(1, within2Months, true),
                createCompletedLending(2, within2Months, true),
                createCompletedLending(3, fiveMonthsAgo, true),
                createCompletedLending(4, eightMonthsAgo, true)
        );
        assertEquals(StandardLendingPolicy.POLICY_NAME,
                resolver.resolvePolicy(reader, regularBook, List.of(), historyWithOld).getPolicyName());

        // Expanding HistoryPeriod to 10 months includes the 8-month-old lending (now 4 >= 4) -> Adaptive!
        resolver.setHistoryPeriodMonths(10);
        assertEquals(AdaptiveLendingPolicy.POLICY_NAME,
                resolver.resolvePolicy(reader, regularBook, List.of(), historyWithOld).getPolicyName());

        // Shrinking HistoryPeriod to 3 months excludes the 5-month and 8-month lendings (only 2 < 4) -> Standard!
        resolver.setHistoryPeriodMonths(3);
        assertEquals(StandardLendingPolicy.POLICY_NAME,
                resolver.resolvePolicy(reader, regularBook, List.of(), historyWithOld).getPolicyName());
    }

    @Test
    @DisplayName("Section 4.2.2 Variation (MinOnTimeRatio): Boundary below and at MinOnTimeRatio")
    void testVaryingMinOnTimeRatioIndependently() {
        LocalDate recent = LocalDate.now().minusMonths(2);
        // 4 completed lendings: 2 on time, 2 late -> 50.0% on-time ratio
        List<Lending> fiftyPercentHistory = List.of(
                createCompletedLending(1, recent, true),
                createCompletedLending(2, recent, true),
                createCompletedLending(3, recent, false),
                createCompletedLending(4, recent, false)
        );

        // With MinOnTimeRatio = 75.0%, 50.0% < 75.0% -> Standard
        assertEquals(StandardLendingPolicy.POLICY_NAME,
                resolver.resolvePolicy(reader, regularBook, List.of(), fiftyPercentHistory).getPolicyName());

        // Lowering MinOnTimeRatio to 50.0% (boundary == 50.0%) -> Adaptive!
        resolver.setMinOnTimeRatio(50.0);
        assertEquals(AdaptiveLendingPolicy.POLICY_NAME,
                resolver.resolvePolicy(reader, regularBook, List.of(), fiftyPercentHistory).getPolicyName());

        // Raising MinOnTimeRatio to 50.1% (boundary > 50.0%) -> Standard!
        resolver.setMinOnTimeRatio(50.1);
        assertEquals(StandardLendingPolicy.POLICY_NAME,
                resolver.resolvePolicy(reader, regularBook, List.of(), fiftyPercentHistory).getPolicyName());
    }

    @Test
    @DisplayName("Section 4.2.3 Variation (Overdue Lending): Presence of any overdue lending forces Standard Policy")
    void testOverdueLendingDisqualifiesAdaptivePolicy() {
        LocalDate recent = LocalDate.now().minusMonths(1);
        // 4 completed lendings, 100% on time
        List<Lending> perfectHistory = List.of(
                createCompletedLending(1, recent, true),
                createCompletedLending(2, recent, true),
                createCompletedLending(3, recent, true),
                createCompletedLending(4, recent, true)
        );

        // Without overdue active lending -> Adaptive
        Lending onTimeActive = createActiveLending(5, false);
        assertEquals(AdaptiveLendingPolicy.POLICY_NAME,
                resolver.resolvePolicy(reader, regularBook, List.of(onTimeActive), perfectHistory).getPolicyName());

        // With 1 overdue active lending -> fails 4.2.3 -> Standard
        Lending overdueActive = createActiveLending(6, true);
        assertEquals(StandardLendingPolicy.POLICY_NAME,
                resolver.resolvePolicy(reader, regularBook, List.of(onTimeActive, overdueActive), perfectHistory).getPolicyName());

        // Also if the overdue unreturned lending is present in the history list
        List<Lending> historyWithOverdueUnreturned = new ArrayList<>(perfectHistory);
        historyWithOverdueUnreturned.add(overdueActive);
        assertEquals(StandardLendingPolicy.POLICY_NAME,
                resolver.resolvePolicy(reader, regularBook, List.of(), historyWithOverdueUnreturned).getPolicyName());
    }

    @Test
    @DisplayName("Section 4.3: Applicable lending policy is re-evaluated dynamically as reader history changes")
    void testPolicyReEvaluatedWhenHistoryChanges() {
        LocalDate recent = LocalDate.now().minusMonths(1);
        List<Lending> history = new ArrayList<>();

        // Initially 3 completed on-time lendings (< 4) -> Standard (duration 15 days)
        history.add(createCompletedLending(1, recent, true));
        history.add(createCompletedLending(2, recent, true));
        history.add(createCompletedLending(3, recent, true));
        LendingPolicy firstEval = resolver.resolvePolicy(reader, regularBook, List.of(), history);
        assertEquals(StandardLendingPolicy.POLICY_NAME, firstEval.getPolicyName());
        assertEquals(15, firstEval.getLendingDurationInDays(List.of()));

        // Reader completes 4th on-time lending -> re-evaluation selects Adaptive (LongPeriod 30 days for 0 active)
        history.add(createCompletedLending(4, recent, true));
        LendingPolicy secondEval = resolver.resolvePolicy(reader, regularBook, List.of(), history);
        assertEquals(AdaptiveLendingPolicy.POLICY_NAME, secondEval.getPolicyName());
        assertEquals(30, secondEval.getLendingDurationInDays(List.of()));

        // Reader later returns two more books late (4 on-time out of 6 = 66.7% < 75.0%) -> re-evaluation drops back to Standard!
        history.add(createCompletedLending(5, recent, false));
        history.add(createCompletedLending(6, recent, false));
        LendingPolicy thirdEval = resolver.resolvePolicy(reader, regularBook, List.of(), history);
        assertEquals(StandardLendingPolicy.POLICY_NAME, thirdEval.getPolicyName());
    }

    @Test
    @DisplayName("Selection parameter constraints validation (Section 4.2 & 5.3)")
    void testSelectionParameterConstraintsValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new LendingPolicyResolver(standardPolicy, adaptivePolicy, 0, 3, 80.0));
        assertThrows(IllegalArgumentException.class,
                () -> new LendingPolicyResolver(standardPolicy, adaptivePolicy, 6, 0, 80.0));
        assertThrows(IllegalArgumentException.class,
                () -> new LendingPolicyResolver(standardPolicy, adaptivePolicy, 6, 3, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new LendingPolicyResolver(standardPolicy, adaptivePolicy, 6, 3, 100.1));

        assertThrows(IllegalArgumentException.class, () -> resolver.setHistoryPeriodMonths(0));
        assertThrows(IllegalArgumentException.class, () -> resolver.setMinCompletedLendings(0));
        assertThrows(IllegalArgumentException.class, () -> resolver.setMinOnTimeRatio(-1.0));
        assertThrows(IllegalArgumentException.class, () -> resolver.setMinOnTimeRatio(101.0));
    }

    @Test
    @DisplayName("Legacy tier resolution and runtime dynamic policy registration/update")
    void testLegacyTierAndDynamicPolicyManagement() {
        LendingPolicyProperties properties = new LendingPolicyProperties();
        LongTermLendingPolicy longTermPolicy = new LongTermLendingPolicy(properties);
        ShortTermLendingPolicy shortTermPolicy = new ShortTermLendingPolicy(properties);
        LendingPolicyResolver multiResolver = new LendingPolicyResolver(
                List.of(standardPolicy, longTermPolicy, shortTermPolicy));

        // 0 active books resolves to LONG_TERM in legacy 3-argument mode
        assertEquals("LONG_TERM", multiResolver.resolvePolicy(reader, regularBook, List.of()).getPolicyName());

        // Reference book resolves to SHORT_TERM
        assertEquals("SHORT_TERM", multiResolver.resolvePolicy(reader, referenceBook, List.of()).getPolicyName());

        // Update policy in runtime
        LendingPolicy updated = multiResolver.updatePolicy("LONG_TERM", 40, 6, 120);
        assertEquals(40, updated.getLendingDurationInDays());
        assertEquals(6, updated.getMaxSimultaneousLendings());
        assertEquals(120, updated.getFineValuePerDayInCents());

        // Register dynamic policy
        DynamicLendingPolicy customPolicy = new DynamicLendingPolicy(
                "VIP_STUDENTS", "Special policy", 45, 7, 50, 50, 0, 5, null, null);
        multiResolver.registerPolicy(customPolicy);
        assertTrue(multiResolver.getPolicyByName("VIP_STUDENTS").isPresent());
        assertEquals("VIP_STUDENTS", multiResolver.resolvePolicy(reader, regularBook, List.of(), List.of()).getPolicyName());

        assertTrue(multiResolver.removePolicy("VIP_STUDENTS"));
        assertFalse(multiResolver.getPolicyByName("VIP_STUDENTS").isPresent());
    }
}
