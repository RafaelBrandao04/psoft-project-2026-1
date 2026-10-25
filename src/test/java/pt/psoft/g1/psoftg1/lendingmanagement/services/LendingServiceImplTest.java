package pt.psoft.g1.psoftg1.lendingmanagement.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingCreatedEvent;
import pt.psoft.g1.psoftg1.lendingmanagement.events.LendingReturnedEvent;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Fine;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.lendingmanagement.repositories.FineRepository;
import pt.psoft.g1.psoftg1.lendingmanagement.repositories.LendingRepository;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
import pt.psoft.g1.psoftg1.shared.services.Page;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure isolated Unit Test for LendingServiceImpl.
 * Strictly adheres to unit test isolation (uses Test Doubles / Mockito, no Spring Context).
 */
@ExtendWith(MockitoExtension.class)
class LendingServiceImplTest {

    @Mock
    private LendingRepository lendingRepository;

    @Mock
    private FineRepository fineRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ReaderRepository readerRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LendingServiceImpl lendingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(lendingService, "lendingDurationInDays", 14);
        ReflectionTestUtils.setField(lendingService, "fineValuePerDayInCents", 154);
    }

    @Test
    void testFindByLendingNumber_Present() {
        Lending lending = mock(Lending.class);
        when(lendingRepository.findByLendingNumber("2026/1")).thenReturn(Optional.of(lending));

        Optional<Lending> result = lendingService.findByLendingNumber("2026/1");

        assertThat(result).isPresent().contains(lending);
        verify(lendingRepository).findByLendingNumber("2026/1");
    }

    @Test
    void testFindByLendingNumber_NotFound() {
        when(lendingRepository.findByLendingNumber("2026/999")).thenReturn(Optional.empty());

        Optional<Lending> result = lendingService.findByLendingNumber("2026/999");

        assertThat(result).isEmpty();
        verify(lendingRepository).findByLendingNumber("2026/999");
    }

    @Test
    void testCreate_ThrowsWhenReaderHasOverdueLending() {
        CreateLendingRequest request = new CreateLendingRequest("9782826012092", "2026/1");
        Lending overdueLending = mock(Lending.class);
        when(overdueLending.getDaysDelayed()).thenReturn(2);
        when(lendingRepository.listOutstandingByReaderNumber("2026/1")).thenReturn(List.of(overdueLending));

        LendingForbiddenException exception = assertThrows(LendingForbiddenException.class,
                () -> lendingService.create(request));

        assertThat(exception.getMessage()).isEqualTo("Reader has book(s) past their due date");
        verify(bookRepository, never()).findByIsbn(anyString());
        verify(lendingRepository, never()).save(any());
    }

    @Test
    void testCreate_ThrowsWhenReaderHasThreeOutstandingLendings() {
        CreateLendingRequest request = new CreateLendingRequest("9782826012092", "2026/1");
        Lending l1 = mock(Lending.class);
        Lending l2 = mock(Lending.class);
        Lending l3 = mock(Lending.class);
        when(l1.getDaysDelayed()).thenReturn(0);
        when(l2.getDaysDelayed()).thenReturn(0);
        when(l3.getDaysDelayed()).thenReturn(0);

        when(lendingRepository.listOutstandingByReaderNumber("2026/1")).thenReturn(List.of(l1, l2, l3));

        LendingForbiddenException exception = assertThrows(LendingForbiddenException.class,
                () -> lendingService.create(request));

        assertThat(exception.getMessage()).isEqualTo("Reader has three books outstanding already");
        verify(bookRepository, never()).findByIsbn(anyString());
        verify(lendingRepository, never()).save(any());
    }

    @Test
    void testCreate_ThrowsWhenBookNotFound() {
        CreateLendingRequest request = new CreateLendingRequest("9782826012092", "2026/1");
        when(lendingRepository.listOutstandingByReaderNumber("2026/1")).thenReturn(List.of());
        when(bookRepository.findByIsbn("9782826012092")).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> lendingService.create(request));

        assertThat(exception.getMessage()).isEqualTo("Book not found");
        verify(readerRepository, never()).findByReaderNumber(anyString());
        verify(lendingRepository, never()).save(any());
    }

    @Test
    void testCreate_ThrowsWhenReaderNotFound() {
        CreateLendingRequest request = new CreateLendingRequest("9782826012092", "2026/1");
        Book book = mock(Book.class);
        when(lendingRepository.listOutstandingByReaderNumber("2026/1")).thenReturn(List.of());
        when(bookRepository.findByIsbn("9782826012092")).thenReturn(Optional.of(book));
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> lendingService.create(request));

        assertThat(exception.getMessage()).isEqualTo("Reader not found");
        verify(lendingRepository, never()).save(any());
    }

    @Test
    void testCreate_Success() {
        CreateLendingRequest request = new CreateLendingRequest("9782826012092", "2026/1");
        Book book = mock(Book.class);
        ReaderDetails reader = mock(ReaderDetails.class);
        when(lendingRepository.listOutstandingByReaderNumber("2026/1")).thenReturn(List.of());
        when(bookRepository.findByIsbn("9782826012092")).thenReturn(Optional.of(book));
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.of(reader));
        when(lendingRepository.getCountFromCurrentYear()).thenReturn(5);

        Lending savedLending = mock(Lending.class);
        when(lendingRepository.save(any(Lending.class))).thenReturn(savedLending);

        Lending result = lendingService.create(request);

        assertThat(result).isSameAs(savedLending);
        org.mockito.ArgumentCaptor<Lending> lendingCaptor = org.mockito.ArgumentCaptor.forClass(Lending.class);
        verify(lendingRepository).save(lendingCaptor.capture());
        Lending capturedLending = lendingCaptor.getValue();
        assertThat(capturedLending).isNotNull();
        assertThat(capturedLending.getLendingNumber()).endsWith("/6");
        verify(eventPublisher).publishEvent(any(LendingCreatedEvent.class));
    }

    @Test
    void testSetReturned_NotFound() {
        when(lendingRepository.findByLendingNumber("2026/1")).thenReturn(Optional.empty());
        SetLendingReturnedRequest request = new SetLendingReturnedRequest("All good");

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> lendingService.setReturned("2026/1", request, 1L));

        assertThat(exception.getMessage()).isEqualTo("Cannot update lending with this lending number");
        verify(fineRepository, never()).save(any());
        verify(lendingRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void testSetReturned_OnTime_NoFine() {
        Lending lending = mock(Lending.class);
        when(lendingRepository.findByLendingNumber("2026/1")).thenReturn(Optional.of(lending));
        when(lending.getDaysDelayed()).thenReturn(0);
        when(lendingRepository.save(lending)).thenReturn(lending);

        SetLendingReturnedRequest request = new SetLendingReturnedRequest("Returned intact");
        Lending result = lendingService.setReturned("2026/1", request, 1L);

        assertThat(result).isSameAs(lending);
        verify(lending).setReturned(1L, "Returned intact");
        verify(fineRepository, never()).save(any());
        verify(lendingRepository).save(lending);
        verify(eventPublisher).publishEvent(any(LendingReturnedEvent.class));
    }

    @Test
    void testSetReturned_Overdue_GeneratesFine() {
        // Here we test the branch where daysDelayed > 0
        Lending lending = mock(Lending.class);
        when(lendingRepository.findByLendingNumber("2026/1")).thenReturn(Optional.of(lending));
        when(lending.getDaysDelayed()).thenReturn(3);
        when(lending.getFineValuePerDayInCents()).thenReturn(150);
        when(lendingRepository.save(lending)).thenReturn(lending);

        SetLendingReturnedRequest request = new SetLendingReturnedRequest("Returned late");
        Lending result = lendingService.setReturned("2026/1", request, 1L);

        assertThat(result).isSameAs(lending);
        verify(lending).setReturned(1L, "Returned late");
        verify(fineRepository).save(any(Fine.class));
        verify(lendingRepository).save(lending);
        verify(eventPublisher).publishEvent(any(LendingReturnedEvent.class));
    }

    @Test
    void testListByReaderNumberAndIsbn_ReturnedOptionalEmpty() {
        List<Lending> lendings = new ArrayList<>(List.of(mock(Lending.class), mock(Lending.class)));
        when(lendingRepository.listByReaderNumberAndIsbn("2026/1", "9782826012092")).thenReturn(lendings);

        List<Lending> result = lendingService.listByReaderNumberAndIsbn("2026/1", "9782826012092", Optional.empty());

        assertThat(result).hasSize(2);
        verify(lendingRepository).listByReaderNumberAndIsbn("2026/1", "9782826012092");
    }

    @Test
    void testListByReaderNumberAndIsbn_FilterReturnedTrue() {
        Lending returnedLending = mock(Lending.class);
        when(returnedLending.getReturnedDate()).thenReturn(LocalDate.now());

        Lending notReturnedLending = mock(Lending.class);
        when(notReturnedLending.getReturnedDate()).thenReturn(null);

        List<Lending> lendings = new ArrayList<>(List.of(returnedLending, notReturnedLending));
        when(lendingRepository.listByReaderNumberAndIsbn("2026/1", "9782826012092")).thenReturn(lendings);

        List<Lending> result = lendingService.listByReaderNumberAndIsbn("2026/1", "9782826012092", Optional.of(true));

        // When returned is true: (returnedDate == null) == true is false for returnedLending,
        // so returnedLending remains in list and notReturned is removed.
        assertThat(result).containsExactly(returnedLending);
    }

    @Test
    void testListByReaderNumberAndIsbn_FilterReturnedFalse() {
        Lending returnedLending = mock(Lending.class);
        when(returnedLending.getReturnedDate()).thenReturn(LocalDate.now());

        Lending notReturnedLending = mock(Lending.class);
        when(notReturnedLending.getReturnedDate()).thenReturn(null);

        List<Lending> lendings = new ArrayList<>(List.of(returnedLending, notReturnedLending));
        when(lendingRepository.listByReaderNumberAndIsbn("2026/1", "9782826012092")).thenReturn(lendings);

        List<Lending> result = lendingService.listByReaderNumberAndIsbn("2026/1", "9782826012092", Optional.of(false));

        assertThat(result).containsExactly(notReturnedLending);
    }

    @Test
    void testGetAverageDuration() {
        when(lendingRepository.getAverageDuration()).thenReturn(14.567);

        Double avg = lendingService.getAverageDuration();

        assertThat(avg).isEqualTo(14.6);
    }

    @Test
    void testGetOverdue_NullPage() {
        List<Lending> overdueList = List.of(mock(Lending.class));
        when(lendingRepository.getOverdue(any(Page.class))).thenReturn(overdueList);

        List<Lending> result = lendingService.getOverdue(null);

        assertThat(result).isSameAs(overdueList);
        verify(lendingRepository).getOverdue(argThat(p -> p.getNumber() == 1 && p.getLimit() == 10));
    }

    @Test
    void testGetOverdue_CustomPage() {
        List<Lending> overdueList = List.of(mock(Lending.class));
        Page customPage = new Page(2, 20);
        when(lendingRepository.getOverdue(customPage)).thenReturn(overdueList);

        List<Lending> result = lendingService.getOverdue(customPage);

        assertThat(result).isSameAs(overdueList);
        verify(lendingRepository).getOverdue(customPage);
    }

    @Test
    void testGetAvgLendingDurationByIsbn() {
        when(lendingRepository.getAvgLendingDurationByIsbn("9782826012092")).thenReturn(8.333);

        Double avg = lendingService.getAvgLendingDurationByIsbn("9782826012092");

        assertThat(avg).isEqualTo(8.3);
    }

    @Test
    void testSearchLendings_InvalidDateFormat_ThrowsException() {
        SearchLendingQuery query = new SearchLendingQuery("2026/1", "9782826012092", true, "invalid-date", null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> lendingService.searchLendings(null, query));

        assertThat(exception.getMessage()).isEqualTo("Expected format is YYYY-MM-DD");
        verify(lendingRepository, never()).searchLendings(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testSearchLendings_SuccessWithValidDates() {
        SearchLendingQuery query = new SearchLendingQuery("2026/1", "9782826012092", true, "2026-01-01", "2026-01-31");
        List<Lending> expected = List.of(mock(Lending.class));
        when(lendingRepository.searchLendings(any(Page.class), eq("2026/1"), eq("9782826012092"), eq(true),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(expected);

        List<Lending> result = lendingService.searchLendings(null, query);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void testSearchLendings_NullQuery() {
        List<Lending> expected = List.of(mock(Lending.class));
        when(lendingRepository.searchLendings(any(Page.class), eq(""), eq(""), isNull(), any(LocalDate.class), isNull()))
                .thenReturn(expected);

        List<Lending> result = lendingService.searchLendings(null, null);

        assertThat(result).isSameAs(expected);
    }
}
