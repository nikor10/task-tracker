package com.nikola.task_tracker_project_spring.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Reads require any authenticated user; POST /api/users is ROLE_ADMIN only.
    private static final String ADMIN = basic("admin", "admin");
    private static final String ALICE = basic("alice", "password123");

    private static String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
    }

    /* ------------------------------- Reads (authenticated) ------------------------------- */

    @Test
    void shouldListAllUsers() throws Exception {
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(3)));
    }

    @Test
    void shouldReturnUserById() throws Exception {
        mockMvc.perform(get("/api/users/1").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void shouldReturnNotFound_whenUserMissing() throws Exception {
        mockMvc.perform(get("/api/users/9999").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldSearchUsersByKeyword() throws Exception {
        mockMvc.perform(get("/api/users/search")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .param("keyword", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    /* ------------------------------- Register (admin only) ------------------------------- */

    @Test
    void shouldCreateUser_whenAdminAndValid() throws Exception {
        String body = """
                {"username":"newuser","email":"newuser@example.com","password":"password123"}
                """;
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.password").doesNotExist()); // WRITE_ONLY
    }

    @Test
    void shouldReturnBadRequest_whenUserInvalid() throws Exception {
        // username too short, password too short, blank email
        String body = """
                {"username":"x","email":"","password":"short"}
                """;
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /* ------------------------------- Auth gating ------------------------------- */

    @Test
    void shouldReturnUnauthorized_whenListingWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorized_whenCreatingWithoutAuth() throws Exception {
        String body = """
                {"username":"nope","email":"nope@example.com","password":"password123"}
                """;
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidRegularUser_fromRegistering() throws Exception {
        // alice is ROLE_USER; registering a new user is an admin-only action.
        String body = """
                {"username":"nope","email":"nope@example.com","password":"password123"}
                """;
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
