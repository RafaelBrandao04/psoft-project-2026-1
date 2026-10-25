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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class StandardLendingPolicyTest {

    private StandardLendingPolicy policy;
    private LendingPolicyProperties properties;
    private ReaderDetails readerDetails;
    private Book book;

    @BeforeEach
    void setUp() {
        properties = new LendingPolicyProperties();
        properties.getStandard().setMaxSimultaneousLendings(3);
        policy = new StandardLendingPolicy(properties);

        List<Author> authors = List.of(new Author("Alexandre Dumas", "Bio", null));
        book = new Book("9782826012092", "Os Três Mosqueteiros", "Descrição", new Genre("Aventura"), authors, null);
        Reader reader = Reader.newReader("standard.reader@example.com", "Password123!", "João Standard");
        readerDetails = new ReaderDetails(1, reader, "1995-05-15", "912345678", true, false, false, null, null);
    }

    @Test
    @DisplayName("Standard policy is always applicable to any reader and book")
    void testIsApplicableAlwaysTrue() {
        assertTrue(policy.isApplicable(readerDetails, book));
        assertTrue(policy.isApplicable(null, null));
        assertTrue(policy.isApplicable(readerDetails, book, List.of()));
    }

    @Test
    @DisplayName("Standard policy returns expected default parameters and priority")
    void testPolicyDefaults() {
        assertEquals("STANDARD", policy.getPolicyName());
        assertEquals(15, policy.getLendingDurationInDays());
        assertEquals(3, policy.getMaxSimultaneousLendings());
        assertEquals(200, policy.getFineValuePerDayInCents());
        assertEquals(10, policy.getPriority());
    }

    @Test
    @DisplayName("Runtime update of duration, max books and fine takes effect immediately")
    void testRuntimeParameterUpdates() {
        policy.setLendingDurationInDays(21);
        policy.setMaxSimultaneousLendings(5);
        policy.setFineValuePerDayInCents(250);
        policy.setPriority(12);

        assertEquals(21, policy.getLendingDurationInDays());
        assertEquals(5, policy.getMaxSimultaneousLendings());
        assertEquals(250, policy.getFineValuePerDayInCents());
        assertEquals(12, policy.getPriority());
    }

    @Test
    @DisplayName("Validation succeeds when reader has zero or fewer than 3 outstanding lendings")
    void testValidateCanLendBoundaryBelowLimit() {
        // 0 lendings
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, book, List.of()));

        // 1 lending (on time)
        Lending l1 = Mockito.mock(Lending.class);
        when(l1.getDaysDelayed()).thenReturn(0);
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, book, List.of(l1)));

        // 2 lendings (N-1)
        Lending l2 = Mockito.mock(Lending.class);
        when(l2.getDaysDelayed()).thenReturn(0);
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, book, List.of(l1, l2)));
    }

    @Test
    @DisplayName("BVA Boundary: Validation throws LendingForbiddenException when reader already has 3 or more outstanding lendings")
    void testValidateCanLendBoundaryAtAndAboveLimit() {
        Lending l1 = Mockito.mock(Lending.class);
        Lending l2 = Mockito.mock(Lending.class);
        Lending l3 = Mockito.mock(Lending.class);
        when(l1.getDaysDelayed()).thenReturn(0);
        when(l2.getDaysDelayed()).thenReturn(0);
        when(l3.getDaysDelayed()).thenReturn(0);

        // Exactly 3 lendings (boundary N) -> forbidden
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, List.of(l1, l2, l3)));

        // 4 lendings (boundary N+1) -> forbidden
        Lending l4 = Mockito.mock(Lending.class);
        when(l4.getDaysDelayed()).thenReturn(0);
        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, List.of(l1, l2, l3, l4)));
    }

    @Test
    @DisplayName("Validation throws LendingForbiddenException when reader has an overdue lending")
    void testValidateCanLendWithOverdueLending() {
        Lending overdue = Mockito.mock(Lending.class);
        when(overdue.getDaysDelayed()).thenReturn(3);

        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, List.of(overdue)));
    }
}
