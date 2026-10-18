package pt.psoft.g1.psoftg1.genremanagement.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pt.psoft.g1.psoftg1.bookmanagement.services.GenreBookCountDTO;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.shared.services.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Isolated Unit Test for GenreServiceImpl using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class GenreServiceImplTest {

    @Mock
    private GenreRepository genreRepository;

    @InjectMocks
    private GenreServiceImpl genreService;

    @Test
    void testFindByString_Found() {
        Genre genre = new Genre("Fantasy");
        when(genreRepository.findByString("Fantasy")).thenReturn(Optional.of(genre));

        Optional<Genre> result = genreService.findByString("Fantasy");

        assertThat(result).isPresent().contains(genre);
    }

    @Test
    void testFindByString_NotFound() {
        when(genreRepository.findByString("NonExistent")).thenReturn(Optional.empty());

        Optional<Genre> result = genreService.findByString("NonExistent");

        assertThat(result).isEmpty();
    }

    @Test
    void testFindAll() {
        List<Genre> genres = List.of(new Genre("Fantasy"), new Genre("Sci-Fi"));
        when(genreRepository.findAll()).thenReturn(genres);

        Iterable<Genre> result = genreService.findAll();

        assertThat(result).containsExactlyElementsOf(genres);
    }

    @Test
    void testSave() {
        Genre genre = new Genre("History");
        when(genreRepository.save(genre)).thenReturn(genre);

        Genre saved = genreService.save(genre);

        assertThat(saved).isSameAs(genre);
        verify(genreRepository).save(genre);
    }

    @Test
    void testFindTopGenreByBooks() {
        GenreBookCountDTO dto = mock(GenreBookCountDTO.class);
        org.springframework.data.domain.Page<GenreBookCountDTO> page = new PageImpl<>(List.of(dto));
        when(genreRepository.findTop5GenreByBookCount(any(Pageable.class))).thenReturn(page);

        List<GenreBookCountDTO> result = genreService.findTopGenreByBooks();

        assertThat(result).containsExactly(dto);
    }

    @Test
    void testGetAverageLendings_NullPage_Defaults() {
        GetAverageLendingsQuery query = new GetAverageLendingsQuery(2026, 1);
        List<GenreLendingsDTO> expected = List.of(mock(GenreLendingsDTO.class));
        when(genreRepository.getAverageLendingsInMonth(eq(LocalDate.of(2026, 1, 1)), any(Page.class)))
                .thenReturn(expected);

        List<GenreLendingsDTO> result = genreService.getAverageLendings(query, null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void testGetAverageLendings_ExplicitPage() {
        GetAverageLendingsQuery query = new GetAverageLendingsQuery(2026, 2);
        Page page = new Page(2, 5);
        List<GenreLendingsDTO> expected = List.of(mock(GenreLendingsDTO.class));
        when(genreRepository.getAverageLendingsInMonth(LocalDate.of(2026, 2, 1), page))
                .thenReturn(expected);

        List<GenreLendingsDTO> result = genreService.getAverageLendings(query, page);

        assertThat(result).isSameAs(expected);
        verify(genreRepository).getAverageLendingsInMonth(LocalDate.of(2026, 2, 1), page);
    }

    @Test
    void testGetLendingsAverageDurationPerMonth_InvalidDateFormat_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> genreService.getLendingsAverageDurationPerMonth("not-a-date", "2026-01-31"));

        assertThat(ex.getMessage()).isEqualTo("Expected format is YYYY-MM-DD");
        verify(genreRepository, never()).getLendingsAverageDurationPerMonth(any(), any());
    }

    @Test
    void testGetLendingsAverageDurationPerMonth_StartDateAfterEndDate_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> genreService.getLendingsAverageDurationPerMonth("2026-05-01", "2026-01-01"));

        assertThat(ex.getMessage()).isEqualTo("Start date cannot be after end date");
        verify(genreRepository, never()).getLendingsAverageDurationPerMonth(any(), any());
    }

    @Test
    void testGetLendingsAverageDurationPerMonth_EmptyList_ThrowsNotFound() {
        when(genreRepository.getLendingsAverageDurationPerMonth(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> genreService.getLendingsAverageDurationPerMonth("2026-01-01", "2026-01-31"));

        assertThat(ex.getMessage()).isEqualTo("No objects match the provided criteria");
    }

    @Test
    void testGetLendingsAverageDurationPerMonth_Success() {
        GenreLendingsPerMonthDTO dto = mock(GenreLendingsPerMonthDTO.class);
        when(genreRepository.getLendingsAverageDurationPerMonth(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(dto));

        List<GenreLendingsPerMonthDTO> result = genreService.getLendingsAverageDurationPerMonth("2026-01-01", "2026-01-31");

        assertThat(result).containsExactly(dto);
    }

    @Test
    void testGetLendingsPerMonthLastYearByGenre() {
        GenreLendingsPerMonthDTO dto = mock(GenreLendingsPerMonthDTO.class);
        when(genreRepository.getLendingsPerMonthLastYearByGenre()).thenReturn(List.of(dto));

        List<GenreLendingsPerMonthDTO> result = genreService.getLendingsPerMonthLastYearByGenre();

        assertThat(result).containsExactly(dto);
        verify(genreRepository).getLendingsPerMonthLastYearByGenre();
    }
}


