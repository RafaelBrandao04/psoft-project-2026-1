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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PremiumReaderLendingPolicyTest {

    private PremiumReaderLendingPolicy policy;
    private LendingPolicyProperties properties;
    private Book book;

    @BeforeEach
    void setUp() {
        properties = new LendingPolicyProperties();
        policy = new PremiumReaderLendingPolicy(properties);

        List<Author> authors = List.of(new Author("Autor Teste", "Bio", null));
        book = new Book("9782826012092", "Livro Teste", "Desc", new Genre("Ficção"), authors, null);
    }

    @Test
    @DisplayName("Premium policy applies when reader username or name reflects premium/academic status")
    void testIsApplicable() {
        Reader premiumUser = Reader.newReader("maria.premium@example.com", "Pass1234!", "Maria Santos");
        ReaderDetails rd1 = new ReaderDetails(1, premiumUser, "1990-01-01", "910000001", true, true, false, null, null);
        assertTrue(policy.isApplicable(rd1, book));

        Reader profUser = Reader.newReader("carlos@isep.ipp.pt", "Pass1234!", "Professor Carlos Silva");
        ReaderDetails rd2 = new ReaderDetails(2, profUser, "1980-01-01", "910000002", true, true, false, null, null);
        assertTrue(policy.isApplicable(rd2, book));

        Reader regularUser = Reader.newReader("antonio@example.com", "Pass1234!", "António Pereira");
        ReaderDetails rd3 = new ReaderDetails(3, regularUser, "2000-01-01", "910000003", true, true, false, null, null);
        assertFalse(policy.isApplicable(rd3, book));

        assertFalse(policy.isApplicable(null, book));
    }

    @Test
    @DisplayName("Premium policy returns expected defaults: duration 30, max 5 books, fine 100, priority 20")
    void testPolicyDefaults() {
        assertEquals("PREMIUM", policy.getPolicyName());
        assertEquals(30, policy.getLendingDurationInDays());
        assertEquals(5, policy.getMaxSimultaneousLendings());
        assertEquals(100, policy.getFineValuePerDayInCents());
        assertEquals(20, policy.getPriority());
    }

    @Test
    @DisplayName("BVA Boundary: Premium reader can borrow up to 4 books without error; 5 or more throws LendingForbiddenException")
    void testValidateCanLendBoundaries() {
        Reader premiumUser = Reader.newReader("ana.premium@example.com", "Pass1234!", "Ana Premium");
        ReaderDetails rd = new ReaderDetails(1, premiumUser, "1992-02-02", "910000010", true, true, false, null, null);

        List<Lending> fourLendings = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Lending l = Mockito.mock(Lending.class);
            when(l.getDaysDelayed()).thenReturn(0);
            fourLendings.add(l);
        }

        // 4 books (N-1) -> allowed
        assertDoesNotThrow(() -> policy.validateCanLend(rd, book, fourLendings));

        // 5 books (boundary N) -> forbidden
        Lending fifth = Mockito.mock(Lending.class);
        when(fifth.getDaysDelayed()).thenReturn(0);
        fourLendings.add(fifth);
        assertThrows(LendingForbiddenException.class, () -> policy.validateCanLend(rd, book, fourLendings));
    }

    @Test
    @DisplayName("Overdue book blocks lending even for premium readers")
    void testValidateCanLendOverdue() {
        Reader premiumUser = Reader.newReader("ana.premium@example.com", "Pass1234!", "Ana Premium");
        ReaderDetails rd = new ReaderDetails(1, premiumUser, "1992-02-02", "910000010", true, true, false, null, null);

        Lending overdue = Mockito.mock(Lending.class);
        when(overdue.getDaysDelayed()).thenReturn(1);

        assertThrows(LendingForbiddenException.class, () -> policy.validateCanLend(rd, book, List.of(overdue)));
    }
}

