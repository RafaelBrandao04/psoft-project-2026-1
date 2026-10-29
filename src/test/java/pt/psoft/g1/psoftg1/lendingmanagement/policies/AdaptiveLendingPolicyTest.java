package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.configuration.LendingPolicyProperties;
import pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException;
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
 * Unit and Mutation Tests for {@link AdaptiveLendingPolicy} (Criterion C3.3).
 *
 * <p>Specifically designed to kill all relational boundary mutants (PITest {@code CONDITIONALS_BOUNDARY},
 * {@code NEGATE_CONDITIONALS}, {@code MATH}, {@code RETURN_VALS}) on:
 * <ul>
 *   <li>3.1. {@code MaxActiveLendings} boundary (N = MaxActiveLendings - 1 vs N = MaxActiveLendings)</li>
 *   <li>3.2 & 3.3. {@code FirstThreshold} boundary (N = FirstThreshold - 1 -> LongPeriod vs N = FirstThreshold -> StandardPeriod)</li>
 *   <li>3.3 & 3.4. {@code SecondThreshold} boundary (N = SecondThreshold - 1 -> StandardPeriod vs N = SecondThreshold -> ShortPeriod)</li>
 *   <li>3.5. Parameter constraints: {@code 0 < FirstThreshold < SecondThreshold < MaxActiveLendings}
 *            and {@code LongPeriod > StandardPeriod > ShortPeriod > 0}</li>
 *   <li>3.6. Renewal validation under adaptive policy</li>
 *   <li>3.7. Overdue lending blocks new lendings</li>
 * </ul>
 */
class AdaptiveLendingPolicyTest {

    // Configured with FirstThreshold = 2, SecondThreshold = 4, MaxActiveLendings = 6
    // LongPeriod = 30, StandardPeriod = 15, ShortPeriod = 7
    private AdaptiveLendingPolicy policy;
    private ReaderDetails readerDetails;
    private Book book;

    @BeforeEach
    void setUp() {
        policy = new AdaptiveLendingPolicy(6, 2, 4, 30, 15, 7, 2, 15);

        List<Author> authors = List.of(new Author("Eça de Queirós", "Bio", null));
        book = new Book("9782826012092", "Os Maias", "Descrição", new Genre("Romance"), authors, null);
        Reader reader = Reader.newReader("adaptive.reader@example.com", "Password123!", "Maria Adaptive");
        readerDetails = new ReaderDetails(1, reader, "1992-03-10", "912345678", true, false, false, null, null);
    }

    private List<Lending> createActiveLendings(int count) {
        List<Lending> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Lending l = Mockito.mock(Lending.class);
            when(l.getDaysDelayed()).thenReturn(0);
            list.add(l);
        }
        return list;
    }

    @Test
    @DisplayName("Section 3.2, 3.3, 3.4 Exact Relational Boundaries on FirstThreshold and SecondThreshold (computeLendingPeriod)")
    void testComputeLendingPeriodExactBoundaries() {
        // Negative count -> IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> policy.computeLendingPeriod(-1));

        // N = 0 (< FirstThreshold=2) -> LongPeriod (30)
        assertEquals(30, policy.computeLendingPeriod(0));

        // N = 1 (FirstThreshold - 1 = 1 < 2) -> LongPeriod (30) [Kills < to <= mutant at FirstThreshold]
        assertEquals(30, policy.computeLendingPeriod(1));

        // N = 2 (Exactly FirstThreshold = 2) -> StandardPeriod (15) [Kills < to <= mutant at FirstThreshold]
        assertEquals(15, policy.computeLendingPeriod(2));

        // N = 3 (SecondThreshold - 1 = 3 < 4) -> StandardPeriod (15) [Kills < to <= mutant at SecondThreshold]
        assertEquals(15, policy.computeLendingPeriod(3));

        // N = 4 (Exactly SecondThreshold = 4) -> ShortPeriod (7) [Kills < to <= mutant at SecondThreshold]
        assertEquals(7, policy.computeLendingPeriod(4));

        // N = 5 (MaxActiveLendings - 1 = 5 < 6) -> ShortPeriod (7) [Kills >= to > mutant at MaxActiveLendings]
        assertEquals(7, policy.computeLendingPeriod(5));

        // N = 6 (Exactly MaxActiveLendings = 6) -> throws LendingForbiddenException
        assertThrows(LendingForbiddenException.class, () -> policy.computeLendingPeriod(6));

        // N = 7 (MaxActiveLendings + 1) -> throws LendingForbiddenException
        assertThrows(LendingForbiddenException.class, () -> policy.computeLendingPeriod(7));
    }

    @Test
    @DisplayName("Section 3.2, 3.3, 3.4 Exact Relational Boundaries via getLendingDurationInDays(activeLendings)")
    void testGetLendingDurationInDaysWithListBoundaries() {
        // null list -> 0 active lendings -> LongPeriod (30)
        assertEquals(30, policy.getLendingDurationInDays(null));

        // 0 active -> 30
        assertEquals(30, policy.getLendingDurationInDays(createActiveLendings(0)));

        // 1 active (FirstThreshold - 1) -> 30
        assertEquals(30, policy.getLendingDurationInDays(createActiveLendings(1)));

        // 2 active (FirstThreshold) -> 15
        assertEquals(15, policy.getLendingDurationInDays(createActiveLendings(2)));

        // 3 active (SecondThreshold - 1) -> 15
        assertEquals(15, policy.getLendingDurationInDays(createActiveLendings(3)));

        // 4 active (SecondThreshold) -> 7
        assertEquals(7, policy.getLendingDurationInDays(createActiveLendings(4)));

        // 5 active (MaxActiveLendings - 1) -> 7
        assertEquals(7, policy.getLendingDurationInDays(createActiveLendings(5)));
    }

    @Test
    @DisplayName("Section 3.1 & 3.7: validateCanLend boundaries on MaxActiveLendings and overdue lendings")
    void testValidateCanLendBoundaries() {
        // null -> allowed
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, book, null));

        // 5 active lendings (MaxActiveLendings - 1) -> allowed
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, book, createActiveLendings(5)));

        // 6 active lendings (MaxActiveLendings) -> forbidden
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, createActiveLendings(6)));

        // 7 active lendings (MaxActiveLendings + 1) -> forbidden
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, createActiveLendings(7)));

        // 1 day overdue (boundary > 0) -> forbidden
        Lending overdue = Mockito.mock(Lending.class);
        when(overdue.getDaysDelayed()).thenReturn(1);
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, List.of(overdue)));
    }

    @Test
    @DisplayName("Section 3.5: Parameter constraints validation kills all boundary mutants")
    void testParameterConstraintsExactBoundaries() {
        // Valid minimal gap configuration: 0 < 1 < 2 < 3 and 3 > 2 > 1 > 0, maxRenewals = 0, renewalPeriod = 1
        assertDoesNotThrow(() -> new AdaptiveLendingPolicy(3, 1, 2, 3, 2, 1, 0, 1));

        // Constraint: FirstThreshold > 0 (boundary at 0)
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 0, 2, 30, 15, 7, 2, 15));
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, -1, 2, 30, 15, 7, 2, 15));

        // Constraint: SecondThreshold > FirstThreshold (boundary when SecondThreshold == FirstThreshold)
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 2, 2, 30, 15, 7, 2, 15));
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 3, 2, 30, 15, 7, 2, 15));

        // Constraint: MaxActiveLendings > SecondThreshold (boundary when MaxActiveLendings == SecondThreshold)
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(4, 2, 4, 30, 15, 7, 2, 15));
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(3, 2, 4, 30, 15, 7, 2, 15));

        // Constraint: ShortPeriod > 0 (boundary at 0)
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 1, 2, 30, 15, 0, 2, 15));
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 1, 2, 30, 15, -1, 2, 15));

        // Constraint: StandardPeriod > ShortPeriod (boundary when StandardPeriod == ShortPeriod)
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 1, 2, 30, 7, 7, 2, 15));
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 1, 2, 30, 6, 7, 2, 15));

        // Constraint: LongPeriod > StandardPeriod (boundary when LongPeriod == StandardPeriod)
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 1, 2, 15, 15, 7, 2, 15));
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 1, 2, 14, 15, 7, 2, 15));

        // Constraint: MaxRenewals >= 0 (boundary at -1)
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 1, 2, 30, 15, 7, -1, 15));

        // Constraint: RenewalPeriod > 0 (boundary at 0)
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveLendingPolicy(5, 1, 2, 30, 15, 7, 2, 0));
    }

    @Test
    @DisplayName("Section 3.6: validateCanRenew and getRenewalPeriodInDays under AdaptiveLendingPolicy")
    void testValidateCanRenewAndAdaptiveRenewalPeriod() {
        Lending lending = new Lending(book, readerDetails, 1, 30, 200);

        // Null lending -> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateCanRenew(null, readerDetails, List.of()));

        // Valid renewal when active lendings satisfy policy
        assertDoesNotThrow(() -> policy.validateCanRenew(lending, readerDetails, List.of(lending)));

        // Adaptive renewal period based on other active lendings:
        // 1 total active (0 other active < FirstThreshold=2) -> LongPeriod (30)
        assertEquals(30, policy.getRenewalPeriodInDays(List.of(lending)));
        // 2 total active (1 other active = FirstThreshold - 1 < 2) -> LongPeriod (30)
        assertEquals(30, policy.getRenewalPeriodInDays(createActiveLendings(2)));
        // 3 total active (2 other active = FirstThreshold = 2) -> StandardPeriod (15)
        assertEquals(15, policy.getRenewalPeriodInDays(createActiveLendings(3)));
        // 4 total active (3 other active = SecondThreshold - 1 = 3) -> StandardPeriod (15)
        assertEquals(15, policy.getRenewalPeriodInDays(createActiveLendings(4)));
        // 5 total active (4 other active = SecondThreshold = 4) -> ShortPeriod (7)
        assertEquals(7, policy.getRenewalPeriodInDays(createActiveLendings(5)));

        // Perform 2 renewals (up to MaxRenewals = 2)
        lending.renew(30);
        assertDoesNotThrow(() -> policy.validateCanRenew(lending, readerDetails, List.of(lending)));
        lending.renew(30);

        // At MaxRenewals = 2 -> forbidden
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanRenew(lending, readerDetails, List.of(lending)));

        // Overdue lending -> forbidden
        Lending overdue = Lending.newBootstrappingLending(
                book, readerDetails, 2026, 2, LocalDate.now().minusDays(40), null, 30, 200);
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanRenew(overdue, readerDetails, List.of(overdue)));

        // Returned lending -> forbidden
        Lending returned = new Lending(book, readerDetails, 3, 30, 200);
        returned.setReturned(0L, "Done");
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanRenew(returned, readerDetails, List.of()));

        // Active lendings equal to MaxActiveLendings (6 <= 6) -> allowed
        Lending freshLending = new Lending(book, readerDetails, 4, 30, 200);
        assertDoesNotThrow(() -> policy.validateCanRenew(freshLending, readerDetails, createActiveLendings(6)));

        // Active lendings exceeding MaxActiveLendings (7 > 6) -> forbidden
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanRenew(freshLending, readerDetails, createActiveLendings(7)));
    }

    @Test
    @DisplayName("Section 4.2: isApplicable on AdaptiveLendingPolicy evaluates history period, minCompleted, onTimeRatio, and overdue")
    void testIsApplicableHistoryEvaluation() {
        assertFalse(policy.isApplicable(readerDetails, book));
        assertFalse(policy.isApplicable(readerDetails, book, List.of()));
        assertFalse(policy.isApplicable(readerDetails, book, List.of(), null));
        assertFalse(policy.isApplicable(readerDetails, book, List.of(), List.of()));

        LocalDate recent = LocalDate.now().minusMonths(1);
        Lending c1 = Lending.newBootstrappingLending(book, readerDetails, 2026, 1, recent, recent.plusDays(5), 15, 200);
        Lending c2 = Lending.newBootstrappingLending(book, readerDetails, 2026, 2, recent, recent.plusDays(5), 15, 200);
        Lending c3 = Lending.newBootstrappingLending(book, readerDetails, 2026, 3, recent, recent.plusDays(5), 15, 200);
        Lending c4Late = Lending.newBootstrappingLending(book, readerDetails, 2026, 4, recent, recent.plusDays(25), 15, 200);

        // 2 completed (< minCompleted=3) -> false
        assertFalse(policy.isApplicable(readerDetails, book, List.of(), List.of(c1, c2)));

        // 3 completed (== minCompleted=3), 100% on-time (>= 80.0%) -> true
        assertTrue(policy.isApplicable(readerDetails, book, List.of(), List.of(c1, c2, c3)));

        // 3 completed, 2 on-time + 1 late = 66.7% (< 80.0%) -> false
        assertFalse(policy.isApplicable(readerDetails, book, List.of(), List.of(c1, c2, c4Late)));

        // Overdue active lending -> false
        Lending overdue = Lending.newBootstrappingLending(book, readerDetails, 2026, 5, LocalDate.now().minusDays(30), null, 15, 200);
        assertFalse(policy.isApplicable(readerDetails, book, List.of(overdue), List.of(c1, c2, c3)));
        assertFalse(policy.isApplicable(readerDetails, book, List.of(), List.of(c1, c2, c3, overdue)));
    }

    @Test
    @DisplayName("Properties constructor and getters/setters verification")
    void testPropertiesConstructorAndGettersSetters() {
        LendingPolicyProperties props = new LendingPolicyProperties();
        AdaptiveLendingPolicy fromProps = new AdaptiveLendingPolicy(props);

        assertEquals("ADAPTIVE", fromProps.getPolicyName());
        assertEquals(5, fromProps.getMaxActiveLendings());
        assertEquals(1, fromProps.getFirstThreshold());
        assertEquals(2, fromProps.getSecondThreshold());
        assertEquals(30, fromProps.getLongPeriod());
        assertEquals(15, fromProps.getStandardPeriod());
        assertEquals(7, fromProps.getShortPeriod());
        assertEquals(2, fromProps.getMaxRenewals());
        assertEquals(15, fromProps.getRenewalPeriodInDays());
        assertEquals(200, fromProps.getFineValuePerDayInCents());
        assertEquals(25, fromProps.getPriority());

        fromProps.setMaxSimultaneousLendings(6);
        fromProps.setSecondThreshold(4);
        fromProps.setFirstThreshold(2);
        fromProps.setLongPeriod(40);
        fromProps.setStandardPeriod(20);
        fromProps.setShortPeriod(10);
        fromProps.setMaxRenewals(3);
        fromProps.setRenewalPeriodInDays(20);
        fromProps.setFineValuePerDayInCents(150);
        fromProps.setPriority(30);

        assertEquals(6, fromProps.getMaxSimultaneousLendings());
        assertEquals(2, fromProps.getFirstThreshold());
        assertEquals(4, fromProps.getSecondThreshold());
        assertEquals(40, fromProps.getLongPeriod());
        assertEquals(20, fromProps.getStandardPeriod());
        assertEquals(10, fromProps.getShortPeriod());
        assertEquals(3, fromProps.getMaxRenewals());
        assertEquals(20, fromProps.getRenewalPeriodInDays());
        assertEquals(150, fromProps.getFineValuePerDayInCents());
        assertEquals(30, fromProps.getPriority());
        assertTrue(fromProps.getDescription().contains("Adaptive"));

        // Invalid setter calls must throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> fromProps.setMaxSimultaneousLendings(4));
        assertThrows(IllegalArgumentException.class, () -> fromProps.setFirstThreshold(4));
        assertThrows(IllegalArgumentException.class, () -> fromProps.setSecondThreshold(2));
        assertThrows(IllegalArgumentException.class, () -> fromProps.setLongPeriod(20));
        assertThrows(IllegalArgumentException.class, () -> fromProps.setStandardPeriod(10));
        assertThrows(IllegalArgumentException.class, () -> fromProps.setShortPeriod(20));
        assertThrows(IllegalArgumentException.class, () -> fromProps.setRenewalPeriodInDays(0));
        assertThrows(IllegalArgumentException.class, () -> fromProps.setMaxRenewals(-1));
        assertThrows(IllegalArgumentException.class, () -> fromProps.setLendingDurationInDays(20));

        // Default constructor
        AdaptiveLendingPolicy defaultPolicy = new AdaptiveLendingPolicy();
        assertEquals(30, defaultPolicy.getLendingDurationInDays());
    }
}

