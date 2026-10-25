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

class LongTermLendingPolicyTest {

    private LongTermLendingPolicy policy;
    private ReaderDetails readerDetails;
    private Book book;

    @BeforeEach
    void setUp() {
        LendingPolicyProperties properties = new LendingPolicyProperties();
        policy = new LongTermLendingPolicy(properties);

        List<Author> authors = List.of(new Author("Alexandre Dumas", "Bio", null));
        book = new Book("9782826012092", "Os Três Mosqueteiros", "Descrição", new Genre("Aventura"), authors, null);
        Reader reader = Reader.newReader("reader@example.com", "Password123!", "Reader");
        readerDetails = new ReaderDetails(1, reader, "1995-05-15", "912345678", true, false, false, null, null);
    }

    @Test
    @DisplayName("Long term policy applies when reader has 0 or 1 active lendings")
    void testApplicability() {
        Lending l1 = Mockito.mock(Lending.class);
        Lending l2 = Mockito.mock(Lending.class);

        // 0 active lendings -> applicable (borrowing book 1)
        assertTrue(policy.isApplicable(readerDetails, book, List.of()));
        // 1 active lending -> applicable (borrowing book 2)
        assertTrue(policy.isApplicable(readerDetails, book, List.of(l1)));
        // 2 active lendings -> not applicable (borrowing book 3, transitions to standard)
        assertFalse(policy.isApplicable(readerDetails, book, List.of(l1, l2)));
    }

    @Test
    @DisplayName("Defaults and runtime parameter updates")
    void testDefaultsAndRuntimeUpdates() {
        assertEquals("LONG_TERM", policy.getPolicyName());
        assertEquals(30, policy.getLendingDurationInDays());
        assertEquals(15, policy.getPriority());

        // Runtime edit
        policy.setLendingDurationInDays(45);
        assertEquals(45, policy.getLendingDurationInDays());
    }

    @Test
    @DisplayName("Validation fails if reader has overdue lendings")
    void testOverdueFails() {
        Lending overdue = Mockito.mock(Lending.class);
        when(overdue.getDaysDelayed()).thenReturn(2);

        assertThrows(LendingForbiddenException.class,
                () -> policy.validateCanLend(readerDetails, book, List.of(overdue)));
    }
}

