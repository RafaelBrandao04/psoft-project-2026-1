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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Functional (Black-Box) Unit Tests for {@link StandardLendingPolicy} (Criterion C3.1).
 *
 * <p>Covers equivalence partitioning and boundary value analysis (BVA) for:
 * <ul>
 *   <li>2.1. {@code MaxActiveLendings} boundary (M-1 allowed, M forbidden, M+1 forbidden)</li>
 *   <li>2.2. {@code LendingPeriod} (> 0 days)</li>
 *   <li>2.3. {@code MaxRenewals} (>= 0 times) and {@code RenewalPeriod} (> 0 days)</li>
 *   <li>2.4. Blocking new lendings when reader has any overdue lending</li>
 *   <li>2.5. Blocking renewal when lending is overdue or already returned</li>
 *   <li>5.3. Parameter constraint validation</li>
 * </ul>
 */
class StandardLendingPolicyTest {

    private StandardLendingPolicy policy;
    private LendingPolicyProperties properties;
    private ReaderDetails readerDetails;
    private Book book;

    @BeforeEach
    void setUp() {
        properties = new LendingPolicyProperties();
        properties.getStandard().setMaxSimultaneousLendings(3);
        properties.getStandard().setDurationInDays(15);
        properties.getStandard().setMaxRenewals(2);
        properties.getStandard().setRenewalPeriodInDays(15);
        policy = new StandardLendingPolicy(properties);

        List<Author> authors = List.of(new Author("Alexandre Dumas", "Bio", null));
        book = new Book("9782826012092", "Os Três Mosqueteiros", "Descrição", new Genre("Aventura"), authors, null);
        Reader reader = Reader.newReader("standard.reader@example.com", "Password123!", "João Standard");
        readerDetails = new ReaderDetails(1, reader, "1995-05-15", "912345678", true, false, false, null, null);
    }

    @Test
    @DisplayName("Standard policy is always applicable by default (Section 4.1)")
    void testIsApplicableAlwaysTrue() {
        assertTrue(policy.isApplicable(readerDetails, book));
        assertTrue(policy.isApplicable(null, null));
        assertTrue(policy.isApplicable(readerDetails, book, List.of()));
        assertTrue(policy.isApplicable(readerDetails, book, List.of(), List.of()));
    }

    @Test
    @DisplayName("Standard policy returns expected parameters (Section 2.1 - 2.3)")
    void testPolicyDefaults() {
        assertEquals("STANDARD", policy.getPolicyName());
        assertEquals(15, policy.getLendingDurationInDays());
        assertEquals(15, policy.getLendingDurationInDays(List.of()));
        assertEquals(3, policy.getMaxSimultaneousLendings());
        assertEquals(3, policy.getMaxActiveLendings());
        assertEquals(2, policy.getMaxRenewals());
        assertEquals(15, policy.getRenewalPeriodInDays());
        assertEquals(200, policy.getFineValuePerDayInCents());
        assertEquals(10, policy.getPriority());
    }

    @Test
    @DisplayName("Runtime update of duration, max books, renewals and fine takes effect immediately")
    void testRuntimeParameterUpdates() {
        policy.setLendingDurationInDays(21);
        policy.setMaxSimultaneousLendings(5);
        policy.setMaxRenewals(3);
        policy.setRenewalPeriodInDays(10);
        policy.setFineValuePerDayInCents(250);
        policy.setPriority(12);

        assertEquals(21, policy.getLendingDurationInDays());
        assertEquals(5, policy.getMaxSimultaneousLendings());
        assertEquals(3, policy.getMaxRenewals());
        assertEquals(10, policy.getRenewalPeriodInDays());
        assertEquals(250, policy.getFineValuePerDayInCents());
        assertEquals(12, policy.getPriority());
    }

    @Test
    @DisplayName("Section 2.1 BVA Below Limit: Validation succeeds when reader has 0, 1, or MaxActiveLendings - 1 active lendings")
    void testValidateCanLendBoundaryBelowLimit() {
        // Null active lendings -> allowed
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, book, null));

        // 0 lendings -> allowed
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, book, List.of()));

        // 1 lending (on time) -> allowed
        Lending l1 = Mockito.mock(Lending.class);
        when(l1.getDaysDelayed()).thenReturn(0);
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, book, List.of(l1)));

        // 2 lendings (MaxActiveLendings - 1 = 2) -> allowed
        Lending l2 = Mockito.mock(Lending.class);
        when(l2.getDaysDelayed()).thenReturn(0);
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, book, List.of(l1, l2)));
    }

    @Test
    @DisplayName("Section 2.1 BVA At and Above Limit: Validation throws LendingForbiddenException at MaxActiveLendings and MaxActiveLendings + 1")
    void testValidateCanLendBoundaryAtAndAboveLimit() {
        Lending l1 = Mockito.mock(Lending.class);
        Lending l2 = Mockito.mock(Lending.class);
        Lending l3 = Mockito.mock(Lending.class);
        when(l1.getDaysDelayed()).thenReturn(0);
        when(l2.getDaysDelayed()).thenReturn(0);
        when(l3.getDaysDelayed()).thenReturn(0);

        // Exactly 3 lendings (boundary MaxActiveLendings) -> forbidden
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, List.of(l1, l2, l3)));

        // 4 lendings (boundary MaxActiveLendings + 1) -> forbidden
        Lending l4 = Mockito.mock(Lending.class);
        when(l4.getDaysDelayed()).thenReturn(0);
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, List.of(l1, l2, l3, l4)));
    }

    @Test
    @DisplayName("Section 2.4: Reader with any overdue lending cannot start a new lending")
    void testValidateCanLendWithOverdueLending() {
        Lending overdue = Mockito.mock(Lending.class);
        when(overdue.getDaysDelayed()).thenReturn(1); // 1 day overdue (boundary)

        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, List.of(overdue)));
    }

    @Test
    @DisplayName("Section 2.3 BVA on MaxRenewals: Renewal allowed up to MaxRenewals - 1, forbidden at MaxRenewals")
    void testValidateCanRenewMaxRenewalsBoundaries() {
        Lending lending = new Lending(book, readerDetails, 1, 15, 200);
        LocalDate initialLimit = lending.getLimitDate();

        // 0 renewals so far (< MaxRenewals=2) -> allowed
        assertDoesNotThrow(() -> policy.validateCanRenew(lending, readerDetails, List.of(lending)));
        lending.renew(policy.getRenewalPeriodInDays());
        assertEquals(1, lending.getRenewalCount());
        assertEquals(initialLimit.plusDays(15), lending.getLimitDate());

        // 1 renewal so far (MaxRenewals - 1 = 1 < 2) -> allowed
        assertDoesNotThrow(() -> policy.validateCanRenew(lending, readerDetails, List.of(lending)));
        lending.renew(policy.getRenewalPeriodInDays());
        assertEquals(2, lending.getRenewalCount());
        assertEquals(initialLimit.plusDays(30), lending.getLimitDate());

        // 2 renewals so far (boundary MaxRenewals = 2) -> forbidden!
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanRenew(lending, readerDetails, List.of(lending)));
    }

    @Test
    @DisplayName("Section 2.3 BVA when MaxRenewals is 0: Zero renewals allowed")
    void testValidateCanRenewWhenMaxRenewalsIsZero() {
        StandardLendingPolicy zeroRenewalsPolicy = new StandardLendingPolicy(3, 15, 0, 15);
        Lending lending = new Lending(book, readerDetails, 1, 15, 200);

        assertThrows(LendingForbiddenException.class,
                () -> zeroRenewalsPolicy.validateCanRenew(lending, readerDetails, List.of(lending)));
    }

    @Test
    @DisplayName("Section 2.5: An overdue lending cannot be renewed")
    void testValidateCanRenewOverdueLendingForbidden() {
        // Lending started 20 days ago with 15-day period -> 5 days overdue
        Lending overdueLending = Lending.newBootstrappingLending(
                book, readerDetails, 2026, 1, LocalDate.now().minusDays(20), null, 15, 200);

        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanRenew(overdueLending, readerDetails, List.of(overdueLending)));

        // Also test when another active lending of the reader is overdue
        Lending onTimeLending = new Lending(book, readerDetails, 2, 15, 200);
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanRenew(onTimeLending, readerDetails, List.of(onTimeLending, overdueLending)));
    }

    @Test
    @DisplayName("Section 2.5: A returned lending cannot be renewed")
    void testValidateCanRenewReturnedLendingForbidden() {
        Lending returnedLending = new Lending(book, readerDetails, 1, 15, 200);
        returnedLending.setReturned(0L, "Returned");

        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanRenew(returnedLending, readerDetails, List.of()));
    }

    @Test
    @DisplayName("Section 5.3: Invalid parameter values throw IllegalArgumentException")
    void testParameterConstraintsValidation() {
        // MaxActiveLendings <= 0
        assertThrows(IllegalArgumentException.class, () -> new StandardLendingPolicy(0, 15, 2, 15));
        assertThrows(IllegalArgumentException.class, () -> new StandardLendingPolicy(-1, 15, 2, 15));

        // LendingPeriod <= 0
        assertThrows(IllegalArgumentException.class, () -> new StandardLendingPolicy(3, 0, 2, 15));
        assertThrows(IllegalArgumentException.class, () -> new StandardLendingPolicy(3, -5, 2, 15));

        // MaxRenewals < 0
        assertThrows(IllegalArgumentException.class, () -> new StandardLendingPolicy(3, 15, -1, 15));

        // RenewalPeriod <= 0
        assertThrows(IllegalArgumentException.class, () -> new StandardLendingPolicy(3, 15, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new StandardLendingPolicy(3, 15, 2, -3));

        // Setters validation
        assertThrows(IllegalArgumentException.class, () -> policy.setLendingDurationInDays(0));
        assertThrows(IllegalArgumentException.class, () -> policy.setMaxSimultaneousLendings(0));
        assertThrows(IllegalArgumentException.class, () -> policy.setMaxRenewals(-1));
        assertThrows(IllegalArgumentException.class, () -> policy.setRenewalPeriodInDays(0));
    }
}
