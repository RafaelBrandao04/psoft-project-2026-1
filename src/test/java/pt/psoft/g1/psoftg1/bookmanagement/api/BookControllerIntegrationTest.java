package pt.psoft.g1.psoftg1.bookmanagement.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookService;
import pt.psoft.g1.psoftg1.readermanagement.services.ReaderService;
import pt.psoft.g1.psoftg1.shared.services.FileStorageService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test for BookController.
 * Validates Spring Security RBAC (Librarian can create books, Readers cannot;
 * Readers have suggestions, Librarians do not) and HTTP routing.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class BookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private ReaderService readerService;

    @Test
    @DisplayName("PUT /api/books/{isbn} without authentication returns 401 Unauthorized")
    void testCreateBook_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/books/9782826012092")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "reader@example.com", roles = "READER")
    @DisplayName("PUT /api/books/{isbn} with READER role returns 403 Forbidden")
    void testCreateBook_WithReaderRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(put("/api/books/9782826012092")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("PUT /api/books/{isbn} with LIBRARIAN role creates book and returns 201 Created")
    void testCreateBook_WithLibrarianRole_ReturnsCreated() throws Exception {
        Book mockBook = mock(Book.class);
        when(mockBook.getIsbn()).thenReturn("9782826012092");
        when(mockBook.getVersion()).thenReturn(1L);

        given(bookService.create(any(), eq("9782826012092"))).willReturn(mockBook);

        mockMvc.perform(put("/api/books/9782826012092")
                        .param("title", "Clean Architecture")
                        .param("genre", "Software")
                        .param("description", "Software craftsmanship principles"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    @WithMockUser(username = "reader@example.com", roles = "READER")
    @DisplayName("GET /api/books/{isbn} with READER role returns 200 OK")
    void testFindByIsbn_WithReaderRole_ReturnsOk() throws Exception {
        Book mockBook = mock(Book.class);
        when(mockBook.getIsbn()).thenReturn("9782826012092");
        when(mockBook.getVersion()).thenReturn(1L);

        given(bookService.findByIsbn("9782826012092")).willReturn(mockBook);

        mockMvc.perform(get("/api/books/9782826012092")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("GET /api/books/suggestions with LIBRARIAN role returns 403 Forbidden")
    void testGetSuggestions_WithLibrarianRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/books/suggestions"))
                .andExpect(status().isForbidden());
    }
}
