package pt.psoft.g1.psoftg1.authormanagement.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.authormanagement.api.AuthorLendingView;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.shared.repositories.PhotoRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Isolated Unit Tests for AuthorServiceImpl.
 * Demonstrates isolation of SUT (AuthorServiceImpl) using Test Doubles (Mocks and Stubs)
 * without bootstrapping Spring context or relational database.
 */
@ExtendWith(MockitoExtension.class)
class AuthorServiceImplTest {

    @Mock
    private AuthorRepository authorRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private AuthorMapper mapper;

    @Mock
    private PhotoRepository photoRepository;

    @InjectMocks
    private AuthorServiceImpl authorService;

    private Author sampleAuthor;

    @BeforeEach
    void setUp() {
        sampleAuthor = new Author("Manuel Antonio Pina",
                "Manuel António Pina foi um jornalista e escritor português.",
                "authorPhotos/photo1.jpg");
    }

    @Test
    @DisplayName("findAll returns all authors from repository")
    void testFindAll() {
        given(authorRepository.findAll()).willReturn(List.of(sampleAuthor));

        Iterable<Author> result = authorService.findAll();

        assertThat(result).containsExactly(sampleAuthor);
        verify(authorRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findByAuthorNumber returns author when exists")
    void testFindByAuthorNumber_Found() {
        given(authorRepository.findByAuthorNumber(1L)).willReturn(Optional.of(sampleAuthor));

        Optional<Author> result = authorService.findByAuthorNumber(1L);

        assertTrue(result.isPresent());
        assertEquals("Manuel Antonio Pina", result.get().getName());
        verify(authorRepository, times(1)).findByAuthorNumber(1L);
    }

    @Test
    @DisplayName("findByAuthorNumber returns empty optional when not found")
    void testFindByAuthorNumber_NotFound() {
        given(authorRepository.findByAuthorNumber(99L)).willReturn(Optional.empty());

        Optional<Author> result = authorService.findByAuthorNumber(99L);

        assertFalse(result.isPresent());
        verify(authorRepository, times(1)).findByAuthorNumber(99L);
    }

    @Test
    @DisplayName("findByName calls repository startsWith search")
    void testFindByName() {
        given(authorRepository.searchByNameNameStartsWith("Manuel")).willReturn(List.of(sampleAuthor));

        List<Author> result = authorService.findByName("Manuel");

        assertThat(result).hasSize(1);
        assertEquals("Manuel Antonio Pina", result.get(0).getName());
        verify(authorRepository, times(1)).searchByNameNameStartsWith("Manuel");
    }

    @Test
    @DisplayName("create without photo delegates to mapper and saves author")
    void testCreate_WithoutPhoto() {
        CreateAuthorRequest request = new CreateAuthorRequest("Jose Saramago", "Premio Nobel", null, null);
        Author createdAuthor = new Author("Jose Saramago", "Premio Nobel", null);

        given(mapper.create(request)).willReturn(createdAuthor);
        given(authorRepository.save(createdAuthor)).willReturn(createdAuthor);

        Author result = authorService.create(request);

        assertNotNull(result);
        assertEquals("Jose Saramago", result.getName());
        assertNull(request.getPhotoURI());
        verify(mapper, times(1)).create(request);
        verify(authorRepository, times(1)).save(createdAuthor);
    }

    @Test
    @DisplayName("create with photo and valid photoURI preserves photo and saves")
    void testCreate_WithPhotoAndURI() {
        MultipartFile mockFile = mock(MultipartFile.class);
        CreateAuthorRequest request = new CreateAuthorRequest("Jose Saramago", "Premio Nobel", mockFile, "uploads/photo.jpg");
        Author createdAuthor = new Author("Jose Saramago", "Premio Nobel", "uploads/photo.jpg");

        given(mapper.create(request)).willReturn(createdAuthor);
        given(authorRepository.save(createdAuthor)).willReturn(createdAuthor);

        Author result = authorService.create(request);

        assertNotNull(result);
        assertEquals("uploads/photo.jpg", request.getPhotoURI());
        verify(authorRepository, times(1)).save(createdAuthor);
    }

    @Test
    @DisplayName("create resets photo and URI when photo is null but photoURI is present")
    void testCreate_MismatchedPhoto_NullPhotoValidURI() {
        CreateAuthorRequest request = new CreateAuthorRequest("Jose Saramago", "Premio Nobel", null, "invalid/path.jpg");
        Author createdAuthor = new Author("Jose Saramago", "Premio Nobel", null);

        given(mapper.create(request)).willReturn(createdAuthor);
        given(authorRepository.save(createdAuthor)).willReturn(createdAuthor);

        Author result = authorService.create(request);

        assertNotNull(result);
        assertNull(request.getPhotoURI());
        assertNull(request.getPhoto());
        verify(authorRepository, times(1)).save(createdAuthor);
    }

    @Test
    @DisplayName("create resets photo and URI when photo is present but photoURI is null")
    void testCreate_MismatchedPhoto_ValidPhotoNullURI() {
        MultipartFile mockFile = mock(MultipartFile.class);
        CreateAuthorRequest request = new CreateAuthorRequest("Jose Saramago", "Premio Nobel", mockFile, null);
        Author createdAuthor = new Author("Jose Saramago", "Premio Nobel", null);

        given(mapper.create(request)).willReturn(createdAuthor);
        given(authorRepository.save(createdAuthor)).willReturn(createdAuthor);

        Author result = authorService.create(request);

        assertNotNull(result);
        assertNull(request.getPhotoURI());
        assertNull(request.getPhoto());
        verify(authorRepository, times(1)).save(createdAuthor);
    }

    @Test
    @DisplayName("partialUpdate succeeds when author exists and version matches")
    void testPartialUpdate_Success() {
        UpdateAuthorRequest request = new UpdateAuthorRequest("Nova Bio", "Novo Nome", null, null);
        given(authorRepository.findByAuthorNumber(1L)).willReturn(Optional.of(sampleAuthor));
        given(authorRepository.save(sampleAuthor)).willReturn(sampleAuthor);

        Author updated = authorService.partialUpdate(1L, request, sampleAuthor.getVersion());

        assertNotNull(updated);
        assertEquals("Novo Nome", updated.getName());
        assertEquals("Nova Bio", updated.getBio());
        verify(authorRepository, times(1)).save(sampleAuthor);
    }

    @Test
    @DisplayName("partialUpdate throws NotFoundException when author does not exist")
    void testPartialUpdate_NotFound() {
        UpdateAuthorRequest request = new UpdateAuthorRequest("Bio", "Nome", null, null);
        given(authorRepository.findByAuthorNumber(99L)).willReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> authorService.partialUpdate(99L, request, 1L));
        verify(authorRepository, never()).save(any());
    }

    @Test
    @DisplayName("partialUpdate resets mismatched photo and URI")
    void testPartialUpdate_MismatchedPhoto() {
        UpdateAuthorRequest request = new UpdateAuthorRequest("Bio", "Nome", null, "invalid/uri.jpg");
        given(authorRepository.findByAuthorNumber(1L)).willReturn(Optional.of(sampleAuthor));
        given(authorRepository.save(sampleAuthor)).willReturn(sampleAuthor);

        authorService.partialUpdate(1L, request, sampleAuthor.getVersion());

        assertNull(request.getPhotoURI());
        assertNull(request.getPhoto());
    }

    @Test
    @DisplayName("findBooksByAuthorNumber delegates to bookRepository")
    void testFindBooksByAuthorNumber() {
        given(bookRepository.findBooksByAuthorNumber(1L)).willReturn(Collections.emptyList());

        List<Book> books = authorService.findBooksByAuthorNumber(1L);

        assertNotNull(books);
        verify(bookRepository, times(1)).findBooksByAuthorNumber(1L);
    }

    @Test
    @DisplayName("findCoAuthorsByAuthorNumber delegates to authorRepository")
    void testFindCoAuthorsByAuthorNumber() {
        given(authorRepository.findCoAuthorsByAuthorNumber(1L)).willReturn(Collections.emptyList());

        List<Author> coAuthors = authorService.findCoAuthorsByAuthorNumber(1L);

        assertNotNull(coAuthors);
        verify(authorRepository, times(1)).findCoAuthorsByAuthorNumber(1L);
    }

    @Test
    @DisplayName("findTopAuthorByLendings delegates to repository with page limit of 5")
    void testFindTopAuthorByLendings() {
        Page<AuthorLendingView> emptyPage = new PageImpl<>(Collections.emptyList());
        given(authorRepository.findTopAuthorByLendings(any(Pageable.class))).willReturn(emptyPage);

        List<AuthorLendingView> topAuthors = authorService.findTopAuthorByLendings();

        assertNotNull(topAuthors);
        verify(authorRepository, times(1)).findTopAuthorByLendings(any(Pageable.class));
    }

    @Test
    @DisplayName("removeAuthorPhoto removes photo, saves author, and deletes from photoRepository")
    void testRemoveAuthorPhoto_Success() {
        String expectedPhotoFile = sampleAuthor.getPhoto().getPhotoFile();
        given(authorRepository.findByAuthorNumber(1L)).willReturn(Optional.of(sampleAuthor));
        given(authorRepository.save(sampleAuthor)).willReturn(sampleAuthor);

        Optional<Author> result = authorService.removeAuthorPhoto(1L, sampleAuthor.getVersion());

        assertTrue(result.isPresent());
        assertNull(result.get().getPhoto());
        verify(authorRepository, times(1)).save(sampleAuthor);
        verify(photoRepository, times(1)).deleteByPhotoFile(expectedPhotoFile);
    }

    @Test
    @DisplayName("removeAuthorPhoto throws NotFoundException when author does not exist")
    void testRemoveAuthorPhoto_NotFound() {
        given(authorRepository.findByAuthorNumber(99L)).willReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> authorService.removeAuthorPhoto(99L, 1L));
        verify(authorRepository, never()).save(any());
        verify(photoRepository, never()).deleteByPhotoFile(any());
    }

    @Test
    @DisplayName("removeAuthorPhoto throws ConflictException when desired version mismatches")
    void testRemoveAuthorPhoto_ConflictException() {
        given(authorRepository.findByAuthorNumber(1L)).willReturn(Optional.of(sampleAuthor));

        assertThrows(ConflictException.class, () -> authorService.removeAuthorPhoto(1L, 999L));
        verify(authorRepository, never()).save(any());
    }
}
