package pt.psoft.g1.psoftg1.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pt.psoft.g1.psoftg1.testutils.JsonHelper;
import pt.psoft.g1.psoftg1.usermanagement.api.UserView;
import pt.psoft.g1.psoftg1.usermanagement.model.Role;
import pt.psoft.g1.psoftg1.usermanagement.services.CreateUserRequest;

import static java.lang.System.currentTimeMillis;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack Integration Tests for Authentication and Registration REST endpoints.
 * Validates user registration, credential authentication, JWT token issuance, and bad input handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String password = "Test12345_";

    @Test
    @DisplayName("POST /api/public/login with valid credentials returns 200 OK and Authorization Bearer header")
    void testLoginSuccess() throws Exception {
        String username = String.format("test.user.%d@nix.io", currentTimeMillis());
        CreateUserRequest registerRequest = new CreateUserRequest(username, "Test User", password);
        registerRequest.setRole(Role.READER);

        this.mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonHelper.toJson(objectMapper, registerRequest)))
                .andExpect(status().isOk());

        AuthRequest loginRequest = new AuthRequest(username, password);

        MvcResult loginResult = this.mockMvc
                .perform(post("/api/public/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonHelper.toJson(objectMapper, loginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.AUTHORIZATION))
                .andReturn();

        UserView authUserView = JsonHelper.fromJson(objectMapper, loginResult.getResponse().getContentAsString(),
                UserView.class);
        assertEquals(username, authUserView.getUsername());
    }

    @Test
    @DisplayName("POST /api/public/login with invalid password returns 401 Unauthorized")
    void testLoginFail() throws Exception {
        String username = String.format("test.user.%d@nix.io", currentTimeMillis());
        CreateUserRequest registerRequest = new CreateUserRequest(username, "Test User", password);
        registerRequest.setRole(Role.READER);

        this.mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonHelper.toJson(objectMapper, registerRequest)))
                .andExpect(status().isOk());

        AuthRequest loginRequest = new AuthRequest(username, "wrongPassword123_");

        this.mockMvc
                .perform(post("/api/public/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonHelper.toJson(objectMapper, loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @DisplayName("POST /api/public/register with valid data returns 200 OK and created user")
    void testRegisterSuccess() throws Exception {
        CreateUserRequest goodRequest = new CreateUserRequest(
                String.format("test.user.%d@nix.com", currentTimeMillis()), "Test User A", password);
        goodRequest.setRole(Role.READER);

        MvcResult createResult = this.mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonHelper.toJson(objectMapper, goodRequest)))
                .andExpect(status().isOk())
                .andReturn();

        UserView userView = JsonHelper.fromJson(objectMapper, createResult.getResponse().getContentAsString(),
                UserView.class);
        assertNotNull(userView.getId(), "User id must not be null!");
        assertEquals(goodRequest.getName(), userView.getFullName());
    }

    @Test
    @DisplayName("POST /api/public/register with blank fields returns 400 Bad Request")
    void testRegisterFail() throws Exception {
        CreateUserRequest badRequest = new CreateUserRequest("invalid.username", "", "");

        this.mockMvc
                .perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonHelper.toJson(objectMapper, badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("must not be blank")));
    }
}
