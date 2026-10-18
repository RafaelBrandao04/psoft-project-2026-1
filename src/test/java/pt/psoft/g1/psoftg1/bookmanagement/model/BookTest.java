package pt.psoft.g1.psoftg1.bookmanagement.model;

import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.bookmanagement.services.UpdateBookRequest;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class BookTest {
    private final String validIsbn = "9782826012092";
    private final String validTitle = "Encantos de contar";
    private final Author validAuthor1 = new Author("João Alberto", "O João Alberto nasceu em Chaves e foi pedreiro a maior parte da sua vida.", null);
    private final Author validAuthor2 = new Author("Maria José", "A Maria José nasceu em Viseu e só come laranjas às segundas feiras.", null);
    private final Genre validGenre = new Genre("Fantasia");
    private ArrayList<Author> authors = new ArrayList<>();

    @BeforeEach
    void setUp(){
        authors.clear();
    }

    @Test
    void ensureIsbnNotNull(){
        authors.add(validAuthor1);
        assertThrows(IllegalArgumentException.class, () -> new Book(null, validTitle, null, validGenre, authors, null));
    }

    @Test
    void ensureTitleNotNull(){
        authors.add(validAuthor1);
        assertThrows(IllegalArgumentException.class, () -> new Book(validIsbn, null, null, validGenre, authors, null));
    }

    @Test
    void ensureGenreNotNull(){
        authors.add(validAuthor1);
        assertThrows(IllegalArgumentException.class, () -> new Book(validIsbn, validTitle, null,null, authors, null));
    }

    @Test
    void ensureAuthorsNotNull(){
        authors.add(validAuthor1);
        assertThrows(IllegalArgumentException.class, () -> new Book(validIsbn, validTitle, null, validGenre, null, null));
    }

    @Test
    void ensureAuthorsNotEmpty(){
        assertThrows(IllegalArgumentException.class, () -> new Book(validIsbn, validTitle, null, validGenre, authors, null));
    }

    @Test
    void ensureBookCreatedWithMultipleAuthors() {
        authors.add(validAuthor1);
        authors.add(validAuthor2);
        assertDoesNotThrow(() -> new Book(validIsbn, validTitle, "A great description", validGenre, authors, "photo.png"));
    }

    @Test
    void testGettersAndInitialState() {
        authors.add(validAuthor1);
        Book book = new Book(validIsbn, validTitle, "A great description", validGenre, authors, "photo.png");

        assertEquals(validIsbn, book.getIsbn());
        assertEquals(validTitle, book.getTitle().toString());
        assertEquals("A great description", book.getDescription());
        assertEquals(validGenre, book.getGenre());
        assertThat(book.getAuthors()).containsExactly(validAuthor1);
        assertNotNull(book.getPhoto());
        assertNull(book.getVersion());
    }

    @Test
    void testRemovePhoto_Success() {
        authors.add(validAuthor1);
        Book book = new Book(validIsbn, validTitle, null, validGenre, authors, "photo.png");
        ReflectionTestUtils.setField(book, "version", 1L);

        book.removePhoto(1L);

        assertNull(book.getPhoto());
    }

    @Test
    void testRemovePhoto_ThrowsOnVersionConflict() {
        authors.add(validAuthor1);
        Book book = new Book(validIsbn, validTitle, null, validGenre, authors, "photo.png");
        ReflectionTestUtils.setField(book, "version", 2L);

        ConflictException exception = assertThrows(ConflictException.class, () -> book.removePhoto(1L));
        assertEquals("Provided version does not match latest version of this object", exception.getMessage());
    }

    @Test
    void testApplyPatch_AllFieldsSuccess() {
        authors.add(validAuthor1);
        Book book = new Book(validIsbn, validTitle, "Old Description", validGenre, authors, "old_photo.png");
        ReflectionTestUtils.setField(book, "version", 0L);

        Genre newGenre = new Genre("Ficção Científica");
        Author newAuthor = new Author("Arthur C Clarke", "Famous sci-fi author", null);
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("2001: A Space Odyssey");
        request.setDescription("A journey to Jupiter");
        request.setGenreObj(newGenre);
        request.setAuthorObjList(List.of(newAuthor));
        request.setPhotoURI("new_photo.jpg");

        book.applyPatch(0L, request);

        assertEquals("2001: A Space Odyssey", book.getTitle().toString());
        assertEquals("A journey to Jupiter", book.getDescription());
        assertEquals(newGenre, book.getGenre());
        assertThat(book.getAuthors()).containsExactly(newAuthor);
        assertNotNull(book.getPhoto());
    }

    @Test
    void testApplyPatch_PartialUpdateLeavesExistingFields() {
        authors.add(validAuthor1);
        Book book = new Book(validIsbn, validTitle, "Old Description", validGenre, authors, "old_photo.png");
        ReflectionTestUtils.setField(book, "version", 1L);

        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Updated Title Only");
        // Other fields remain null

        book.applyPatch(1L, request);

        assertEquals("Updated Title Only", book.getTitle().toString());
        assertEquals("Old Description", book.getDescription());
        assertEquals(validGenre, book.getGenre());
        assertThat(book.getAuthors()).containsExactly(validAuthor1);
    }

    @Test
    void testApplyPatch_ThrowsOnStaleObjectVersion() {
        authors.add(validAuthor1);
        Book book = new Book(validIsbn, validTitle, null, validGenre, authors, null);
        ReflectionTestUtils.setField(book, "version", 2L);

        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("New Title");

        assertThrows(StaleObjectStateException.class, () -> book.applyPatch(1L, request));
    }

    @Test
    void testDefaultConstructorForOrm() {
        Book book = new Book();
        assertNotNull(book);
    }
}