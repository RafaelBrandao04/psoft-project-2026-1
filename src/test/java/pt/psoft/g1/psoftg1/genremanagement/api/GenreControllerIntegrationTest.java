package pt.psoft.g1.psoftg1.genremanagement.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.psoft.g1.psoftg1.bookmanagement.services.GenreBookCountDTO;
import pt.psoft.g1.psoftg1.genremanagement.services.GenreLendingsPerMonthDTO;
import pt.psoft.g1.psoftg1.genremanagement.services.GenreService;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test for GenreController.
 * Validates Spring Security RBAC (Librarian can view analytics/top genres, Readers cannot)
 * and HTTP routing.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class GenreControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GenreService genreService;

    @Test
    @DisplayName("GET /api/genres/top5 without authentication returns 401 Unauthorized")
    void testGetTopGenres_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/genres/top5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "reader@example.com", roles = "READER")
    @DisplayName("GET /api/genres/top5 with READER role returns 403 Forbidden")
    void testGetTopGenres_WithReaderRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/genres/top5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("GET /api/genres/top5 with LIBRARIAN role returns 200 OK")
    void testGetTopGenres_WithLibrarianRole_ReturnsOk() throws Exception {
        GenreBookCountDTO dto = new GenreBookCountDTO("Fantasy", 15L);

        given(genreService.findTopGenreByBooks()).willReturn(List.of(dto));

        mockMvc.perform(get("/api/genres/top5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "reader@example.com", roles = "READER")
    @DisplayName("GET /api/genres/lendingsPerMonthLastTwelveMonths with READER role returns 403 Forbidden")
    void testGetLendingsPerMonth_WithReaderRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/genres/lendingsPerMonthLastTwelveMonths")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("GET /api/genres/lendingsPerMonthLastTwelveMonths with LIBRARIAN role returns 200 OK")
    void testGetLendingsPerMonth_WithLibrarianRole_ReturnsOk() throws Exception {
        GenreLendingsPerMonthDTO dto = new GenreLendingsPerMonthDTO(2026, 1, List.of());

        given(genreService.getLendingsPerMonthLastYearByGenre()).willReturn(List.of(dto));

        mockMvc.perform(get("/api/genres/lendingsPerMonthLastTwelveMonths")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
