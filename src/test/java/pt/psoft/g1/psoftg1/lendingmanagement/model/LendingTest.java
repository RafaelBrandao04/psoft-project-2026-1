package pt.psoft.g1.psoftg1.lendingmanagement.model;

import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.usermanagement.model.Reader;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LendingTest {
    private static final ArrayList<Author> authors = new ArrayList<>();
    private static Book book;
    private static ReaderDetails readerDetails;
    private final int lendingDurationInDays = 14;
    private final int fineValuePerDayInCents = 150;

    @BeforeAll
    public static void setup(){
        Author author = new Author("Manuel Antonio Pina",
                "Manuel António Pina foi um jornalista e escritor português, premiado em 2011 com o Prémio Camões",
                null);
        authors.add(author);
        book = new Book("9782826012092",
                "O Inspetor Max",
                "conhecido pastor-alemão que trabalha para a Judiciária, vai ser fundamental para resolver um importante caso de uma rede de malfeitores que quer colocar uma bomba num megaconcerto de uma ilustre cantora",
                new Genre("Romance"),
                authors,
                null);
        readerDetails = new ReaderDetails(1,
                Reader.newReader("manuel@gmail.com", "Manuelino123!", "Manuel Sarapinto das Coives"),
                "2000-01-01",
                "919191919",
                true,
                true,
                true,
                null,
                null);
    }

    @Test
    void ensureBookNotNull(){
        assertThrows(IllegalArgumentException.class, () -> new Lending(null, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents));
    }

    @Test
    void ensureReaderNotNull(){
        assertThrows(IllegalArgumentException.class, () -> new Lending(book, null, 1, lendingDurationInDays, fineValuePerDayInCents));
    }

    @Test
    void ensureValidReaderNumber(){
        assertThrows(IllegalArgumentException.class, () -> new Lending(book, readerDetails, -1, lendingDurationInDays, fineValuePerDayInCents));
    }

    @Test
    void testSetReturned_SuccessWithCommentary(){
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        lending.setReturned(0L, "Excelente livro");

        assertEquals(LocalDate.now(), lending.getReturnedDate());
    }

    @Test
    void testSetReturned_ThrowsWhenAlreadyReturned(){
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        lending.setReturned(0L, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> lending.setReturned(0L, "Outro"));
        assertEquals("book has already been returned!", ex.getMessage());
    }

    @Test
    void testSetReturned_ThrowsOnStaleVersion(){
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        ReflectionTestUtils.setField(lending, "version", 2L);

        assertThrows(StaleObjectStateException.class, () -> lending.setReturned(1L, "Comentário"));
    }

    @Test
    void testGetDaysDelayed_NotOverdue(){
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        assertEquals(0, lending.getDaysDelayed());
        assertEquals(Optional.empty(), lending.getFineValueInCents());
        assertEquals(Optional.empty(), lending.getDaysOverdue());
    }

    @Test
    void testGetDaysDelayed_OverdueNotReturned(){
        // Create lending that was due 5 days ago
        LocalDate pastStart = LocalDate.now().minusDays(19);
        Lending lending = Lending.newBootstrappingLending(book, readerDetails, 2026, 1, pastStart, null, 14, 200);

        assertEquals(5, lending.getDaysDelayed());
        assertEquals(Optional.of(5), lending.getDaysOverdue());
        assertEquals(Optional.of(1000), lending.getFineValueInCents());
        assertEquals(Optional.empty(), lending.getDaysUntilReturn());
    }

    @Test
    void testGetDaysDelayed_OverdueReturnedLate(){
        LocalDate pastStart = LocalDate.of(2026, 1, 1);
        LocalDate returnedDate = LocalDate.of(2026, 1, 20); // 5 days late (14 days allowed)
        Lending lending = Lending.newBootstrappingLending(book, readerDetails, 2026, 1, pastStart, returnedDate, 14, 150);

        assertEquals(5, lending.getDaysDelayed());
        assertEquals(Optional.of(750), lending.getFineValueInCents());
        assertEquals(Optional.empty(), lending.getDaysUntilReturn());
    }

    @Test
    void testGetDaysUntilReturn_ActiveLending(){
        Lending lending = new Lending(book, readerDetails, 1, 14, fineValuePerDayInCents);
        assertEquals(Optional.of(14), lending.getDaysUntilReturn());
    }

    @Test
    void testGetDaysUntilReturn_ReturnedLendingReturnsEmpty(){
        Lending lending = new Lending(book, readerDetails, 1, 14, fineValuePerDayInCents);
        lending.setReturned(0L, null);
        assertEquals(Optional.empty(), lending.getDaysUntilReturn());
    }

    @Test
    void testGetTitle() {
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        assertEquals("O Inspetor Max", lending.getTitle());
    }

    @Test
    void testGetLendingNumber() {
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        assertEquals(LocalDate.now().getYear() + "/1", lending.getLendingNumber());
    }

    @Test
    void testGetBook() {
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        assertEquals(book, lending.getBook());
    }

    @Test
    void testGetReaderDetails() {
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        assertEquals(readerDetails, lending.getReaderDetails());
    }

    @Test
    void testGetStartDate() {
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        assertEquals(LocalDate.now(), lending.getStartDate());
    }

    @Test
    void testGetLimitDate() {
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        assertEquals(LocalDate.now().plusDays(lendingDurationInDays), lending.getLimitDate());
    }

    @Test
    void testGetReturnedDate() {
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, fineValuePerDayInCents);
        assertNull(lending.getReturnedDate());
    }

    @Test
    void testGetFineValuePerDayInCents() {
        Lending lending = new Lending(book, readerDetails, 1, lendingDurationInDays, 250);
        assertEquals(250, lending.getFineValuePerDayInCents());
    }

    @Test
    void testBootstrappingLending_Validation() {
        assertThrows(IllegalArgumentException.class, () ->
                Lending.newBootstrappingLending(null, readerDetails, 2026, 1, LocalDate.now(), null, 14, 150));
        assertThrows(IllegalArgumentException.class, () ->
                Lending.newBootstrappingLending(book, null, 2026, 1, LocalDate.now(), null, 14, 150));
    }

    @Test
    void testDefaultConstructorForOrm() {
        Lending lending = new Lending();
        assertNotNull(lending);
    }
}
