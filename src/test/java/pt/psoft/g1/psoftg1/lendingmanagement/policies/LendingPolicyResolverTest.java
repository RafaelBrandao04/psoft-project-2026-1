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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LendingPolicyResolverTest {

    private LendingPolicyResolver resolver;
    private StandardLendingPolicy standardPolicy;
    private LongTermLendingPolicy longTermPolicy;
    private ShortTermLendingPolicy shortTermPolicy;
    private PremiumReaderLendingPolicy premiumPolicy;

    private ReaderDetails standardReader;
    private ReaderDetails premiumReader;
    private Book regularBook;
    private Book referenceBook;

    @BeforeEach
    void setUp() {
        LendingPolicyProperties properties = new LendingPolicyProperties();
        standardPolicy = new StandardLendingPolicy(properties);
        longTermPolicy = new LongTermLendingPolicy(properties);
        shortTermPolicy = new ShortTermLendingPolicy(properties);
        premiumPolicy = new PremiumReaderLendingPolicy(properties);

        resolver = new LendingPolicyResolver(List.of(standardPolicy, longTermPolicy, shortTermPolicy, premiumPolicy));

        List<Author> authors = List.of(new Author("Autor", "Bio", null));
        regularBook = new Book("9782826012092", "Romance Histórico", "Desc", new Genre("Romance"), authors, null);
        referenceBook = new Book("8175257660", "Dicionário de Referência", "Desc", new Genre("Reference"), authors, null);

        Reader reader1 = Reader.newReader("standard@example.com", "Pass1234!", "Leitor Normal");
        standardReader = new ReaderDetails(1, reader1, "1990-01-01", "910000001", true, false, false, null, null);

        Reader reader2 = Reader.newReader("prof.silva@example.com", "Pass1234!", "Professor Silva");
        premiumReader = new ReaderDetails(2, reader2, "1980-01-01", "910000002", true, true, false, null, null);
    }

    @Test
    @DisplayName("0 or 1 active books resolves to LongTermLendingPolicy (long period)")
    void testResolveLongTermForLowActiveLendings() {
        // 0 active books (borrowing book 1)
        LendingPolicy p0 = resolver.resolvePolicy(standardReader, regularBook, List.of());
        assertEquals("LONG_TERM", p0.getPolicyName());
        assertEquals(30, p0.getLendingDurationInDays());

        // 1 active book (borrowing book 2)
        Lending l1 = Mockito.mock(Lending.class);
        LendingPolicy p1 = resolver.resolvePolicy(standardReader, regularBook, List.of(l1));
        assertEquals("LONG_TERM", p1.getPolicyName());
        assertEquals(30, p1.getLendingDurationInDays());
    }

    @Test
    @DisplayName("2 active books resolves to StandardLendingPolicy (standard period for 3rd book)")
    void testResolveStandardForThirdBook() {
        Lending l1 = Mockito.mock(Lending.class);
        Lending l2 = Mockito.mock(Lending.class);

        LendingPolicy p2 = resolver.resolvePolicy(standardReader, regularBook, List.of(l1, l2));
        assertEquals("STANDARD", p2.getPolicyName());
        assertEquals(15, p2.getLendingDurationInDays());
    }

    @Test
    @DisplayName("3 or more active books resolves to ShortTermLendingPolicy (short period for 4th+ book)")
    void testResolveShortTermForFourthBook() {
        Lending l1 = Mockito.mock(Lending.class);
        Lending l2 = Mockito.mock(Lending.class);
        Lending l3 = Mockito.mock(Lending.class);

        LendingPolicy p3 = resolver.resolvePolicy(standardReader, regularBook, List.of(l1, l2, l3));
        assertEquals("SHORT_TERM", p3.getPolicyName());
        assertEquals(7, p3.getLendingDurationInDays());
    }

    @Test
    @DisplayName("Reference books resolve to ShortTermLendingPolicy regardless of active books count")
    void testReferenceBookResolvesToShortTerm() {
        LendingPolicy p = resolver.resolvePolicy(standardReader, referenceBook, List.of());
        assertEquals("SHORT_TERM", p.getPolicyName());
    }

    @Test
    @DisplayName("Runtime update of policy parameters by LMS Admin")
    void testUpdatePolicyInRuntime() {
        // Update long term policy duration to 40 days
        LendingPolicy updated = resolver.updatePolicy("LONG_TERM", 40, 6, 120);
        assertEquals(40, updated.getLendingDurationInDays());
        assertEquals(6, updated.getMaxSimultaneousLendings());
        assertEquals(120, updated.getFineValuePerDayInCents());

        // Verify resolution immediately uses the updated period
        LendingPolicy resolved = resolver.resolvePolicy(standardReader, regularBook, List.of());
        assertEquals(40, resolved.getLendingDurationInDays());
    }

    @Test
    @DisplayName("Runtime creation and registration of new dynamic lending policy")
    void testRegisterNewDynamicPolicyInRuntime() {
        DynamicLendingPolicy customPolicy = new DynamicLendingPolicy(
                "VIP_STUDENTS",
                "Special policy for VIP students",
                45,
                7,
                50,
                50, // higher priority
                0,
                5,
                null,
                null
        );

        resolver.registerPolicy(customPolicy);

        assertTrue(resolver.getPolicyByName("VIP_STUDENTS").isPresent());

        // Now resolves to VIP_STUDENTS because of priority 50
        LendingPolicy resolved = resolver.resolvePolicy(standardReader, regularBook, List.of());
        assertEquals("VIP_STUDENTS", resolved.getPolicyName());
        assertEquals(45, resolved.getLendingDurationInDays());

        // Delete policy
        boolean removed = resolver.removePolicy("VIP_STUDENTS");
        assertTrue(removed);
        assertFalse(resolver.getPolicyByName("VIP_STUDENTS").isPresent());
    }
}
