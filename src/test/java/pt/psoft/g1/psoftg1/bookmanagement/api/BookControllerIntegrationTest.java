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
import pt.psoft.g1.psoftg1.bookmanagement.services.BookDetailsDTO;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookService;
import pt.psoft.g1.psoftg1.readermanagement.services.ReaderService;
import pt.psoft.g1.psoftg1.shared.services.FileStorageService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test for BookController (SUT = Controller + Service + Spring Security).
 * Validates Spring Security RBAC, HTTP routing, and external bibliographic endpoints.
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
    @WithMockUser(username = "reader@example.com", roles = "READER")
    @DisplayName("GET /api/books/external/{isbn} with READER role returns 200 OK and external BookDetailsDTO")
    void testFindExternalBookByIsbn_WithReaderRole_ReturnsOk() throws Exception {
        BookDetailsDTO details = new BookDetailsDTO(
                "9780132350884",
                "Clean Code",
                "Craftsmanship handbook",
                List.of("Robert C. Martin"),
                "Computers / Programming",
                "Prentice Hall",
                "2008",
                "http://example.com/cover.jpg",
                "googlebooks"
        );

        given(bookService.findExternalBookInfo("9780132350884")).willReturn(Optional.of(details));

        mockMvc.perform(get("/api/books/external/9780132350884")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("9780132350884"))
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.authors[0]").value("Robert C. Martin"))
                .andExpect(jsonPath("$.genre").value("Computers / Programming"))
                .andExpect(jsonPath("$.sourceProvider").value("googlebooks"));
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("GET /api/books/external/{isbn} when not found in external sources returns 404 Not Found")
    void testFindExternalBookByIsbn_NotFound_Returns404() throws Exception {
        given(bookService.findExternalBookInfo("9780000000000")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/books/external/9780000000000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/books/external/{isbn} without authentication returns 401 Unauthorized")
    void testFindExternalBookByIsbn_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/books/external/9780132350884")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("GET /api/books/suggestions with LIBRARIAN role returns 403 Forbidden")
    void testGetSuggestions_WithLibrarianRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/books/suggestions"))
                .andExpect(status().isForbidden());
    }
}
