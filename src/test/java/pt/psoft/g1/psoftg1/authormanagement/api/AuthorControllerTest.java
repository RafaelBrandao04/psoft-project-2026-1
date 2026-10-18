package pt.psoft.g1.psoftg1.authormanagement.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.services.AuthorService;
import pt.psoft.g1.psoftg1.authormanagement.services.UpdateAuthorRequest;
import pt.psoft.g1.psoftg1.bookmanagement.api.BookView;
import pt.psoft.g1.psoftg1.bookmanagement.api.BookViewMapper;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.shared.api.ListResponse;
import pt.psoft.g1.psoftg1.shared.model.Photo;
import pt.psoft.g1.psoftg1.shared.services.ConcurrencyService;
import pt.psoft.g1.psoftg1.shared.services.FileStorageService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Isolated Unit Tests for AuthorController.
 * Follows the 3/6 Dimensions testing model from lecture materials:
 * - Level: Unit test
 * - Strategy: Opaque-box / Transparent-box with Mockito isolation
 * - SUT: AuthorController
 * - Collaborators isolated via Test Doubles (Mocks): AuthorService, ViewMappers, FileStorageService
 */
@ExtendWith(MockitoExtension.class)
class AuthorControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthorService authorService;

    @Mock
    private AuthorViewMapper authorViewMapper;

    @Mock
    private ConcurrencyService concurrencyService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private BookViewMapper bookViewMapper;

    @InjectMocks
    private AuthorController authorController;

    private Author sampleAuthor;
    private AuthorView sampleAuthorView;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authorController).build();

        sampleAuthor = new Author("Manuel Antonio Pina",
                "Manuel António Pina foi um jornalista e escritor português.",
                "authorPhotos/photo1.jpg");

        sampleAuthorView = new AuthorView();
        sampleAuthorView.setAuthorNumber(1L);
        sampleAuthorView.setName("Manuel Antonio Pina");
        sampleAuthorView.setBio("Manuel António Pina foi um jornalista e escritor português.");
        sampleAuthorView.setPhoto("api/authors/1/photo");
    }

    @Test
    @DisplayName("GET /api/authors/{authorNumber} returns 200 OK with eTag and author view")
    void testFindByAuthorNumber_Success() throws Exception {
        given(authorService.findByAuthorNumber(1L)).willReturn(Optional.of(sampleAuthor));
        given(authorViewMapper.toAuthorView(sampleAuthor)).willReturn(sampleAuthorView);

        mockMvc.perform(get("/api/authors/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.authorNumber", is(1)))
                .andExpect(jsonPath("$.name", is("Manuel Antonio Pina")));

        verify(authorService, times(1)).findByAuthorNumber(1L);
    }

    @Test
    @DisplayName("GET /api/authors?name={name} returns 200 OK with list of authors")
    void testFindByName_Success() throws Exception {
        given(authorService.findByName("Manuel")).willReturn(List.of(sampleAuthor));
        given(authorViewMapper.toAuthorView(List.of(sampleAuthor))).willReturn(List.of(sampleAuthorView));

        mockMvc.perform(get("/api/authors")
                        .param("name", "Manuel")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name", is("Manuel Antonio Pina")));

        verify(authorService, times(1)).findByName("Manuel");
    }

    @Test
    @DisplayName("GET /api/authors/{authorNumber}/books returns 200 OK with book views")
    void testGetBooksByAuthorNumber_Success() throws Exception {
        BookView bookView = new BookView();
        bookView.setIsbn("9789720049605");
        bookView.setTitle("Os Piratas");

        given(authorService.findByAuthorNumber(1L)).willReturn(Optional.of(sampleAuthor));
        given(authorService.findBooksByAuthorNumber(1L)).willReturn(Collections.emptyList());
        given(bookViewMapper.toBookView(anyList())).willReturn(List.of(bookView));

        mockMvc.perform(get("/api/authors/1/books")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].isbn", is("9789720049605")));

        verify(authorService, times(1)).findByAuthorNumber(1L);
        verify(authorService, times(1)).findBooksByAuthorNumber(1L);
    }

    @Test
    @DisplayName("PATCH /api/authors/{authorNumber} without If-Match header returns 400 Bad Request")
    void testPartialUpdate_MissingIfMatch_ReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/authors/1")
                        .param("name", "Nome Atualizado")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest());

        verify(authorService, never()).partialUpdate(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("PATCH /api/authors/{authorNumber} with valid If-Match returns 200 OK")
    void testPartialUpdate_Success() throws Exception {
        given(concurrencyService.getVersionFromIfMatchHeader("\"0\"")).willReturn(0L);
        given(authorService.partialUpdate(eq(1L), any(UpdateAuthorRequest.class), eq(0L))).willReturn(sampleAuthor);
        given(authorViewMapper.toAuthorView(sampleAuthor)).willReturn(sampleAuthorView);

        mockMvc.perform(patch("/api/authors/1")
                        .header("If-Match", "\"0\"")
                        .param("name", "Manuel Antonio Pina")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.name", is("Manuel Antonio Pina")));

        verify(authorService, times(1)).partialUpdate(eq(1L), any(UpdateAuthorRequest.class), eq(0L));
    }

    @Test
    @DisplayName("DELETE /api/authors/{authorNumber}/photo when author has no photo returns 404 Not Found")
    void testDeleteAuthorPhoto_NoPhoto_ReturnsNotFound() throws Exception {
        Author authorWithoutPhoto = new Author("Jose Saramago", "Nobel", null);
        given(authorService.findByAuthorNumber(2L)).willReturn(Optional.of(authorWithoutPhoto));

        mockMvc.perform(delete("/api/authors/2/photo"))
                .andExpect(status().isNotFound());

        verify(fileStorageService, never()).deleteFile(anyString());
        verify(authorService, never()).removeAuthorPhoto(anyLong(), anyLong());
    }

    @Test
    @DisplayName("DELETE /api/authors/{authorNumber}/photo when author has photo deletes file and returns 200 OK")
    void testDeleteAuthorPhoto_Success() throws Exception {
        String expectedPhotoFile = sampleAuthor.getPhoto().getPhotoFile();
        given(authorService.findByAuthorNumber(1L)).willReturn(Optional.of(sampleAuthor));
        doNothing().when(fileStorageService).deleteFile(expectedPhotoFile);
        given(authorService.removeAuthorPhoto(sampleAuthor.getAuthorNumber(), sampleAuthor.getVersion()))
                .willReturn(Optional.of(sampleAuthor));

        mockMvc.perform(delete("/api/authors/1/photo"))
                .andExpect(status().isOk());

        verify(fileStorageService, times(1)).deleteFile(expectedPhotoFile);
        verify(authorService, times(1)).removeAuthorPhoto(sampleAuthor.getAuthorNumber(), sampleAuthor.getVersion());
    }
}
