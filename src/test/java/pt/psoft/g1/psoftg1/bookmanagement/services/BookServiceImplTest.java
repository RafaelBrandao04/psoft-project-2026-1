package pt.psoft.g1.psoftg1.bookmanagement.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
import pt.psoft.g1.psoftg1.shared.model.Photo;
import pt.psoft.g1.psoftg1.shared.repositories.PhotoRepository;
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
 * Isolated Unit Test for BookServiceImpl using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private AuthorRepository authorRepository;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private ReaderRepository readerRepository;

    @Mock
    private BookInfoProvider bookInfoProvider;

    @InjectMocks
    private BookServiceImpl bookService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bookService, "suggestionsLimitPerGenre", 5L);
    }

    @Test
    void testCreate_ThrowsConflict_WhenIsbnAlreadyExists() {
        CreateBookRequest request = new CreateBookRequest();
        when(bookRepository.findByIsbn("9782826012092")).thenReturn(Optional.of(mock(Book.class)));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> bookService.create(request, "9782826012092"));

        assertThat(exception.getMessage()).contains("already exists");
        verify(bookRepository, never()).save(any());
    }

    @Test
    void testCreate_ThrowsNotFound_WhenGenreDoesNotExist() {
        CreateBookRequest request = new CreateBookRequest();
        request.setGenre("Fantasy");
        request.setAuthors(List.of(1L));

        when(bookRepository.findByIsbn("9782826012092")).thenReturn(Optional.empty());
        when(authorRepository.findByAuthorNumber(1L)).thenReturn(Optional.of(mock(Author.class)));
        when(genreRepository.findByString("Fantasy")).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> bookService.create(request, "9782826012092"));

        assertThat(exception.getMessage()).isEqualTo("Genre not found");
        verify(bookRepository, never()).save(any());
    }

    @Test
    void testCreate_Success() {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Clean Code");
        request.setDescription("A handbook of agile software craftsmanship");
        request.setGenre("Software");
        request.setAuthors(List.of(1L, 99L)); // 99L does not exist and should be skipped

        Author author1 = mock(Author.class);
        Genre genre = new Genre("Software");

        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.empty());
        when(authorRepository.findByAuthorNumber(1L)).thenReturn(Optional.of(author1));
        when(authorRepository.findByAuthorNumber(99L)).thenReturn(Optional.empty());
        when(genreRepository.findByString("Software")).thenReturn(Optional.of(genre));

        Book savedBook = mock(Book.class);
        when(bookRepository.save(any(Book.class))).thenReturn(savedBook);

        Book result = bookService.create(request, "9780132350884");

        assertThat(result).isSameAs(savedBook);
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    void testCreate_InconsistentPhoto_ResetsPhoto() {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Book Title");
        request.setDescription("Desc");
        request.setGenre("Software");
        request.setAuthors(List.of(1L));
        request.setPhoto(mock(MultipartFile.class));
        request.setPhotoURI(null);

        Author author = mock(Author.class);
        Genre genre = new Genre("Software");
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.empty());
        when(authorRepository.findByAuthorNumber(1L)).thenReturn(Optional.of(author));
        when(genreRepository.findByString("Software")).thenReturn(Optional.of(genre));
        when(bookRepository.save(any(Book.class))).thenAnswer(i -> i.getArgument(0));

        Book result = bookService.create(request, "9780132350884");

        assertThat(result).isNotNull();
        assertThat(request.getPhoto()).isNull();
        assertThat(request.getPhotoURI()).isNull();
    }

    @Test
    void testCreate_EnrichedFromExternalSource_WhenFieldsMissing() {
        CreateBookRequest request = new CreateBookRequest();
        // Missing title, description, genre
        request.setAuthors(List.of(1L));

        BookDetailsDTO externalDto = new BookDetailsDTO(
                "9780132350884",
                "Clean Code (Enriched)",
                "Agile craftsmanship (Enriched)",
                List.of("Uncle Bob"),
                "Software",
                "Prentice Hall",
                "2008",
                null,
                "googlebooks"
        );

        Author author = mock(Author.class);
        Genre genre = new Genre("Software");

        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.empty());
        when(bookInfoProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.of(externalDto));
        when(authorRepository.findByAuthorNumber(1L)).thenReturn(Optional.of(author));
        when(genreRepository.findByString("Software")).thenReturn(Optional.of(genre));
        when(bookRepository.save(any(Book.class))).thenAnswer(i -> i.getArgument(0));

        Book result = bookService.create(request, "9780132350884");

        assertThat(result).isNotNull();
        assertThat(request.getTitle()).isEqualTo("Clean Code (Enriched)");
        assertThat(request.getDescription()).isEqualTo("Agile craftsmanship (Enriched)");
        assertThat(request.getGenre()).isEqualTo("Software");
    }

    @Test
    void testUpdate_ThrowsNotFound_WhenGenreNotFound() {
        UpdateBookRequest request = new UpdateBookRequest();
        request.setIsbn("9780132350884");
        request.setGenre("NonExistentGenre");

        Book book = mock(Book.class);
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(book));
        when(genreRepository.findByString("NonExistentGenre")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> bookService.update(request, "1"));
    }

    @Test
    void testUpdate_Success() {
        UpdateBookRequest request = new UpdateBookRequest();
        request.setIsbn("9780132350884");
        request.setAuthors(List.of(1L, 99L));
        request.setGenre("Fantasy");

        Author author = mock(Author.class);
        Genre genre = new Genre("Fantasy");
        Book book = mock(Book.class);

        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(book));
        when(authorRepository.findByAuthorNumber(1L)).thenReturn(Optional.of(author));
        when(authorRepository.findByAuthorNumber(99L)).thenReturn(Optional.empty());
        when(genreRepository.findByString("Fantasy")).thenReturn(Optional.of(genre));

        Book result = bookService.update(request, "1");

        assertThat(result).isSameAs(book);
        verify(book).applyPatch(1L, request);
        verify(bookRepository).save(book);
    }

    @Test
    void testSave() {
        Book book = mock(Book.class);
        when(bookRepository.save(book)).thenReturn(book);

        Book saved = bookService.save(book);

        assertThat(saved).isSameAs(book);
        verify(bookRepository).save(book);
    }

    @Test
    void testFindTop5BooksLent() {
        BookCountDTO dto = mock(BookCountDTO.class);
        org.springframework.data.domain.Page<BookCountDTO> page = new PageImpl<>(List.of(dto));
        when(bookRepository.findTop5BooksLent(any(LocalDate.class), any(Pageable.class))).thenReturn(page);

        List<BookCountDTO> result = bookService.findTop5BooksLent();

        assertThat(result).containsExactly(dto);
    }

    @Test
    void testFindByGenre() {
        Book book = mock(Book.class);
        when(bookRepository.findByGenre("Fantasy")).thenReturn(List.of(book));

        List<Book> result = bookService.findByGenre("Fantasy");

        assertThat(result).containsExactly(book);
    }

    @Test
    void testFindByTitle() {
        Book book = mock(Book.class);
        when(bookRepository.findByTitle("Clean Code")).thenReturn(List.of(book));

        List<Book> result = bookService.findByTitle("Clean Code");

        assertThat(result).containsExactly(book);
    }

    @Test
    void testFindByAuthorName() {
        Book book = mock(Book.class);
        when(bookRepository.findByAuthorName("Martin%")).thenReturn(List.of(book));

        List<Book> result = bookService.findByAuthorName("Martin");

        assertThat(result).containsExactly(book);
    }

    @Test
    void testFindByIsbn_NotFound() {
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> bookService.findByIsbn("9780132350884"));
    }

    @Test
    void testFindByIsbn_Found() {
        Book book = mock(Book.class);
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(book));

        Book result = bookService.findByIsbn("9780132350884");

        assertThat(result).isSameAs(book);
    }

    @Test
    void testFindExternalBookInfo_Success() {
        BookDetailsDTO dto = new BookDetailsDTO("9780132350884", "Clean Code", "Desc", List.of("Author"), "Genre");
        when(bookInfoProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.of(dto));

        Optional<BookDetailsDTO> result = bookService.findExternalBookInfo("9780132350884");

        assertThat(result).isPresent().contains(dto);
    }

    @Test
    void testFindExternalBookInfo_NotFound() {
        when(bookInfoProvider.findBookByIsbn("9780132350884")).thenReturn(Optional.empty());

        Optional<BookDetailsDTO> result = bookService.findExternalBookInfo("9780132350884");

        assertThat(result).isEmpty();
    }

    @Test
    void testRemoveBookPhoto_ThrowsNotFound_WhenNoPhotoAssigned() {
        Book book = mock(Book.class);
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(book));
        when(book.getPhoto()).thenReturn(null);

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> bookService.removeBookPhoto("9780132350884", 1L));

        assertThat(exception.getMessage()).isEqualTo("Book did not have a photo assigned to it.");
        verify(photoRepository, never()).deleteByPhotoFile(anyString());
    }

    @Test
    void testRemoveBookPhoto_Success() {
        Book book = mock(Book.class);
        Photo photo = mock(Photo.class);
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(book));
        when(book.getPhoto()).thenReturn(photo);
        when(photo.getPhotoFile()).thenReturn("photo_123.jpg");
        when(bookRepository.save(book)).thenReturn(book);

        Book result = bookService.removeBookPhoto("9780132350884", 1L);

        assertThat(result).isSameAs(book);
        verify(book).removePhoto(1L);
        verify(photoRepository).deleteByPhotoFile("photo_123.jpg");
        verify(bookRepository).save(book);
    }

    @Test
    void testGetBooksSuggestionsForReader_ThrowsNotFound_WhenReaderNotFound() {
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> bookService.getBooksSuggestionsForReader("2026/1"));

        assertThat(exception.getMessage()).isEqualTo("Reader not found with provided login");
    }

    @Test
    void testGetBooksSuggestionsForReader_ThrowsNotFound_WhenReaderHasNoInterests() {
        ReaderDetails readerDetails = mock(ReaderDetails.class);
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.of(readerDetails));
        when(readerDetails.getInterestList()).thenReturn(new ArrayList<>());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> bookService.getBooksSuggestionsForReader("2026/1"));

        assertThat(exception.getMessage()).isEqualTo("Reader has no interests");
    }

    @Test
    void testGetBooksSuggestionsForReader_Success_RespectsGenreLimit() {
        ReaderDetails readerDetails = mock(ReaderDetails.class);
        Genre genre = new Genre("Sci-Fi");
        when(readerRepository.findByReaderNumber("2026/1")).thenReturn(Optional.of(readerDetails));
        when(readerDetails.getInterestList()).thenReturn(List.of(genre));

        // Create 7 books in Sci-Fi, but limit is 5
        List<Book> sciFiBooks = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            sciFiBooks.add(mock(Book.class));
        }
        when(bookRepository.findByGenre("Sci-Fi")).thenReturn(sciFiBooks);

        List<Book> suggestions = bookService.getBooksSuggestionsForReader("2026/1");

        assertThat(suggestions).hasSize(5);
    }

    @Test
    void testSearchBooks_DefaultsWhenNull() {
        List<Book> books = List.of(mock(Book.class));
        when(bookRepository.searchBooks(any(Page.class), any(SearchBooksQuery.class))).thenReturn(books);

        List<Book> result = bookService.searchBooks(null, null);

        assertThat(result).isSameAs(books);
        verify(bookRepository).searchBooks(
                argThat(p -> p.getNumber() == 1 && p.getLimit() == 10),
                argThat(q -> "".equals(q.getTitle()) && "".equals(q.getGenre()) && "".equals(q.getAuthorName()))
        );
    }
}
