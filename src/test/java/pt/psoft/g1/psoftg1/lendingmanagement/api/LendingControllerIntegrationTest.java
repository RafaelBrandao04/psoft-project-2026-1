package pt.psoft.g1.psoftg1.lendingmanagement.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.lendingmanagement.services.CreateLendingRequest;
import pt.psoft.g1.psoftg1.lendingmanagement.services.LendingService;
import pt.psoft.g1.psoftg1.readermanagement.services.ReaderService;
import pt.psoft.g1.psoftg1.usermanagement.services.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test for LendingController.
 * Verifies HTTP routing, JSON serialization, and Spring Security RBAC
 * (Librarian can create lendings, Readers cannot; Readers return lendings, etc.).
 */
@SpringBootTest
@AutoConfigureMockMvc
public class LendingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LendingService lendingService;

    @MockBean
    private ReaderService readerService;

    @MockBean
    private UserService userService;

    @MockBean
    private LendingViewMapper lendingViewMapper;

    @Test
    @DisplayName("POST /api/lendings without authentication returns 401 Unauthorized")
    void testCreateLending_Unauthenticated_ReturnsUnauthorized() throws Exception {
        CreateLendingRequest request = new CreateLendingRequest("9782826012092", "2026/1");

        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "reader@example.com", roles = "READER")
    @DisplayName("POST /api/lendings with READER role returns 403 Forbidden")
    void testCreateLending_WithReaderRole_ReturnsForbidden() throws Exception {
        CreateLendingRequest request = new CreateLendingRequest("9782826012092", "2026/1");

        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("POST /api/lendings with LIBRARIAN role returns 201 Created")
    void testCreateLending_WithLibrarianRole_ReturnsCreated() throws Exception {
        CreateLendingRequest request = new CreateLendingRequest("9782826012092", "2026/1");

        Lending mockLending = mock(Lending.class);
        when(mockLending.getLendingNumber()).thenReturn("2026/1");
        when(mockLending.getVersion()).thenReturn(1L);

        given(lendingService.create(any(CreateLendingRequest.class))).willReturn(mockLending);
        given(lendingViewMapper.toLendingView(any(Lending.class))).willReturn(new LendingView());

        mockMvc.perform(post("/api/lendings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    @WithMockUser(username = "reader@example.com", roles = "READER")
    @DisplayName("PATCH /api/lendings/{year}/{seq} without If-Match header returns 400 Bad Request")
    void testSetLendingReturned_WithoutIfMatch_ReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/lendings/2026/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentary\":\"Returned intact\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("PATCH /api/lendings/{year}/{seq} with LIBRARIAN role returns 403 Forbidden")
    void testSetLendingReturned_WithLibrarianRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/lendings/2026/1")
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentary\":\"Returned intact\"}"))
                .andExpect(status().isForbidden());
    }
}
