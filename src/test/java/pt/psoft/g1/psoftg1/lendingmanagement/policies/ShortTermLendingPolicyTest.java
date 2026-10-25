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

class ShortTermLendingPolicyTest {

    private ShortTermLendingPolicy policy;
    private LendingPolicyProperties properties;
    private ReaderDetails readerDetails;

    @BeforeEach
    void setUp() {
        properties = new LendingPolicyProperties();
        properties.getShortTerm().setMaxSimultaneousLendings(1);
        policy = new ShortTermLendingPolicy(properties);

        Reader reader = Reader.newReader("leitor@example.com", "Pass1234!", "Leitor");
        readerDetails = new ReaderDetails(1, reader, "1998-08-08", "919999999", true, true, false, null, null);
    }

    @Test
    @DisplayName("ShortTerm policy applies when book genre or title matches reference / short-term criteria")
    void testIsApplicable() {
        List<Author> authors = List.of(new Author("Autor", "Bio", null));
        Book referenceBook = new Book("9782826012092", "Enciclopédia Geral", "Desc", new Genre("Reference"), authors, null);
        assertTrue(policy.isApplicable(readerDetails, referenceBook));

        Book shortTermByTitle = new Book("8175257660", "Guia de Referência Rápida", "Desc", new Genre("Informática"), authors, null);
        assertTrue(policy.isApplicable(readerDetails, shortTermByTitle));

        Book regularBook = new Book("9782826012092", "Memorial do Convento", "Desc", new Genre("Romance"), authors, null);
        assertFalse(policy.isApplicable(readerDetails, regularBook));

        assertFalse(policy.isApplicable(readerDetails, null));
    }

    @Test
    @DisplayName("ShortTerm policy applies when reader already has 3 or more active lendings (borrowing 4th book)")
    void testApplicableOnHighActiveLendings() {
        List<Author> authors = List.of(new Author("Autor", "Bio", null));
        Book regularBook = new Book("9782826012092", "Memorial do Convento", "Desc", new Genre("Romance"), authors, null);

        Lending l1 = Mockito.mock(Lending.class);
        Lending l2 = Mockito.mock(Lending.class);
        Lending l3 = Mockito.mock(Lending.class);

        // 2 active books -> not applicable for regular book
        assertFalse(policy.isApplicable(readerDetails, regularBook, List.of(l1, l2)));

        // 3 active books -> applicable (borrowing 4th book)
        assertTrue(policy.isApplicable(readerDetails, regularBook, List.of(l1, l2, l3)));
    }

    @Test
    @DisplayName("ShortTerm policy defaults and runtime updates")
    void testPolicyDefaultsAndRuntimeUpdates() {
        assertEquals("SHORT_TERM", policy.getPolicyName());
        assertEquals(7, policy.getLendingDurationInDays());
        assertEquals(1, policy.getMaxSimultaneousLendings());
        assertEquals(400, policy.getFineValuePerDayInCents());

        policy.setLendingDurationInDays(10);
        assertEquals(10, policy.getLendingDurationInDays());
    }

    @Test
    @DisplayName("BVA Boundary: Reader with 1 short-term book cannot exceed max simultaneous limit")
    void testValidateCanLendBoundaries() {
        List<Author> authors = List.of(new Author("Autor", "Bio", null));
        Book refBook = new Book("9782826012092", "Enciclopédia", "Desc", new Genre("Reference"), authors, null);

        // 0 books currently borrowed -> allowed
        assertDoesNotThrow(() -> policy.validateCanLend(readerDetails, refBook, List.of()));

        // 1 book already borrowed -> forbidden (limit is 1 in setUp)
        Lending l1 = Mockito.mock(Lending.class);
        when(l1.getDaysDelayed()).thenReturn(0);

        assertThrows(LendingForbiddenException.class, () -> policy.validateCanLend(readerDetails, refBook, List.of(l1)));
    }
}
