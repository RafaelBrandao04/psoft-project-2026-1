package pt.psoft.g1.psoftg1.authormanagement.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.services.AuthorService;
import pt.psoft.g1.psoftg1.authormanagement.services.CreateAuthorRequest;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test for AuthorController web layer.
 * Validates full Spring Security integration, HTTP routing,
 * and Role-Based Access Control (RBAC: READER vs LIBRARIAN).
 */
@SpringBootTest
@AutoConfigureMockMvc
public class AuthorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthorService authorService;

    @Test
    @DisplayName("GET /api/authors/{authorNumber} without authentication returns 401 Unauthorized")
    void testFindByAuthorNumber_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/authors/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "reader1@example.com", roles = "READER")
    @DisplayName("GET /api/authors/{authorNumber} with READER role returns 200 OK")
    void testFindByAuthorNumber_WithReaderRole_ReturnsOk() throws Exception {
        Author author = new Author("Manuel Antonio Pina", "Bio...", null);
        ReflectionTestUtils.setField(author, "authorNumber", 1L);
        ReflectionTestUtils.setField(author, "version", 0L);

        given(authorService.findByAuthorNumber(1L)).willReturn(Optional.of(author));

        mockMvc.perform(get("/api/authors/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Manuel Antonio Pina")));
    }

    @Test
    @WithMockUser(username = "reader1@example.com", roles = "READER")
    @DisplayName("POST /api/authors with READER role returns 403 Forbidden (requires LIBRARIAN)")
    void testCreateAuthor_WithReaderRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/authors")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Jose Saramago")
                        .param("bio", "Bio..."))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "librarian1@example.com", roles = "LIBRARIAN")
    @DisplayName("POST /api/authors with LIBRARIAN role returns 201 Created")
    void testCreateAuthor_WithLibrarianRole_ReturnsCreated() throws Exception {
        Author author = new Author("Jose Saramago", "Nobel prize author", null);
        ReflectionTestUtils.setField(author, "authorNumber", 2L);
        ReflectionTestUtils.setField(author, "version", 0L);

        given(authorService.create(any(CreateAuthorRequest.class))).willReturn(author);

        mockMvc.perform(post("/api/authors")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Jose Saramago")
                        .param("bio", "Nobel prize author"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Jose Saramago")));
    }
}
