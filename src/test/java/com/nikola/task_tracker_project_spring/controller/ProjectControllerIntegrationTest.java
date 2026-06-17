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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // admin/admin is the in-memory ROLE_ADMIN account; alice is a seeded ROLE_USER
    // (owns projects #1 and #2). Both authenticate via the DB-backed UserDetailsService.
    private static final String ADMIN = basic("admin", "admin");
    private static final String ALICE = basic("alice", "password123");
    // charlie owns no project but is assigned tasks in project #1 (alice's) and #3 (bob's).
    private static final String CHARLIE = basic("charlie", "password123");

    private static String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
    }

    /* ------------------------------- Reads (admin) ------------------------------- */

    @Test
    void shouldReturnPagedProjects_forAdmin() throws Exception {
        mockMvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void shouldReturnProjectById_forAdmin() throws Exception {
        mockMvc.perform(get("/api/projects/1").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Website Redesign"));
    }

    @Test
    void shouldReturnNotFound_whenProjectMissing() throws Exception {
        mockMvc.perform(get("/api/projects/9999").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnProjectsByOwner_forAdmin() throws Exception {
        // Seed data: owner #1 (alice) owns two projects.
        mockMvc.perform(get("/api/projects/by-owner/1").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /* ------------------------------- Writes (admin) ------------------------------ */

    @Test
    void shouldCreateProject_whenAdminAndValid() throws Exception {
        String body = """
                {"name":"New Project","description":"desc","owner":{"id":1}}
                """;
        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("New Project"));
    }

    @Test
    void shouldUpdateProject_whenAdmin() throws Exception {
        String body = """
                {"name":"Renamed","description":"updated"}
                """;
        mockMvc.perform(put("/api/projects/1")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void shouldDeleteProject_thenReturnNotFound() throws Exception {
        mockMvc.perform(delete("/api/projects/3").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/3").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequest_whenNameTooShort() throws Exception {
        String body = """
                {"name":"ab","description":"d","owner":{"id":1}}
                """;
        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /* --------------------------- Auth & per-user scoping ------------------------- */

    @Test
    void shouldReturnUnauthorized_whenListingWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorized_whenCreatingWithoutAuth() throws Exception {
        String body = """
                {"name":"No Auth","description":"d","owner":{"id":1}}
                """;
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnOnlyOwnProjects_forRegularUser() throws Exception {
        // alice owns exactly two projects; she should not see bob's.
        mockMvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void shouldForbidRegularUser_fromReadingAnothersProject() throws Exception {
        // Project #3 (Data Pipeline) is owned by bob (#2), not alice.
        mockMvc.perform(get("/api/projects/3").header(HttpHeaders.AUTHORIZATION, ALICE))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldOwnNewProject_whenRegularUserCreates() throws Exception {
        // alice supplies a different owner, but the server forces ownership to her.
        String body = """
                {"name":"Alices Project","description":"mine","owner":{"id":2}}
                """;
        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.username").value("alice"));
    }

    /* ------------------------- Accessible projects (task browsing) ------------------------- */

    @Test
    void accessibleProjects_includeOnesWhereUserIsOnlyAnAssignee() throws Exception {
        // The bug fix: charlie owns no project but is assigned tasks in #1 and #3, so both must
        // appear — even though he is NOT their owner.
        mockMvc.perform(get("/api/projects/accessible").header(HttpHeaders.AUTHORIZATION, CHARLIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.containsInAnyOrder(1, 3)));
    }

    @Test
    void accessibleProjects_returnOwnedProjects_forRegularUser() throws Exception {
        // alice owns #1 and #2 (and is assigned only within her own project), so exactly those two.
        mockMvc.perform(get("/api/projects/accessible").header(HttpHeaders.AUTHORIZATION, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.containsInAnyOrder(1, 2)));
    }

    @Test
    void accessibleProjects_returnAll_forAdmin() throws Exception {
        mockMvc.perform(get("/api/projects/accessible").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void accessibleProjects_requireAuth() throws Exception {
        mockMvc.perform(get("/api/projects/accessible"))
                .andExpect(status().isUnauthorized());
    }
}
