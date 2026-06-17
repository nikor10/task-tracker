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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // alice (#1) owns project #1 (tasks 1-3). project #3 is bob's.
    private static final String ADMIN = basic("admin", "admin");
    private static final String ALICE = basic("alice", "password123");
    private static final String CHARLIE = basic("charlie", "password123");

    private static String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
    }

    /* ------------------------------- Reads (admin) ------------------------------- */

    @Test
    void shouldReturnTaskById_forAdmin() throws Exception {
        mockMvc.perform(get("/api/tasks/1").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Set up repo"));
    }

    @Test
    void shouldReturnNotFound_whenTaskMissing() throws Exception {
        mockMvc.perform(get("/api/tasks/9999").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnPagedTasksForProject_forAdmin() throws Exception {
        mockMvc.perform(get("/api/projects/1/tasks").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void shouldFilterTasksByStatus_forAdmin() throws Exception {
        mockMvc.perform(get("/api/projects/1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("TODO"))));
    }

    @Test
    void shouldReturnTasksForUser_forAdmin() throws Exception {
        mockMvc.perform(get("/api/users/1/tasks").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldReturnTasksDueToday_forAdmin() throws Exception {
        mockMvc.perform(get("/api/tasks/due-today").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /* ------------------------------- Writes (admin) ------------------------------ */

    @Test
    void shouldCreateTask_whenAdminAndValid() throws Exception {
        String body = """
                {"title":"Brand new task","description":"d","status":"TODO","priority":"HIGH","dueDate":"2026-09-01"}
                """;
        mockMvc.perform(post("/api/projects/1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Brand new task"));
    }

    @Test
    void shouldUpdateTask_whenAdmin() throws Exception {
        String body = """
                {"title":"Updated title","description":"d","status":"COMPLETED","priority":"LOW","dueDate":"2026-01-01"}
                """;
        mockMvc.perform(put("/api/tasks/2")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldDeleteTask_thenReturnNotFound() throws Exception {
        mockMvc.perform(delete("/api/tasks/5").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/5").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnOverdueTasks_afterCreatingAnOverdueOne() throws Exception {
        String body = """
                {"title":"Way overdue","description":"d","status":"TODO","priority":"HIGH","dueDate":"2020-01-01"}
                """;
        mockMvc.perform(post("/api/projects/1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/overdue").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[*].title",
                        org.hamcrest.Matchers.hasItem("Way overdue")));
    }

    @Test
    void shouldReturnBadRequest_whenTaskInvalid() throws Exception {
        // title too short and status/priority missing (NotNull)
        String body = """
                {"title":"x"}
                """;
        mockMvc.perform(post("/api/projects/1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /* --------------------------- Auth & per-user scoping ------------------------- */

    @Test
    void shouldReturnUnauthorized_whenReadingTaskWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorized_whenCreatingTaskWithoutAuth() throws Exception {
        String body = """
                {"title":"No auth","status":"TODO","priority":"LOW"}
                """;
        mockMvc.perform(post("/api/projects/1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowRegularUser_toReadTaskInOwnProject() throws Exception {
        // Task #1 belongs to project #1, owned by alice.
        mockMvc.perform(get("/api/tasks/1").header(HttpHeaders.AUTHORIZATION, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Set up repo"));
    }

    @Test
    void shouldForbidRegularUser_fromReadingTaskInAnothersProject() throws Exception {
        // Task #6 is in project #3 (bob's) and assigned to charlie — not alice.
        mockMvc.perform(get("/api/tasks/6").header(HttpHeaders.AUTHORIZATION, ALICE))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnEmpty_whenRegularUserListsProjectWithoutTheirTasks() throws Exception {
        // alice owns neither project #3 nor any task in it (tasks #6, #7 belong to charlie/bob).
        mockMvc.perform(get("/api/projects/3/tasks").header(HttpHeaders.AUTHORIZATION, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void shouldReturnOnlyAssignedTasks_whenRegularUserListsAnothersProject() throws Exception {
        // charlie does not own project #1 (alice does) but is the assignee of task #3.
        mockMvc.perform(get("/api/projects/1/tasks").header(HttpHeaders.AUTHORIZATION, CHARLIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Write landing copy"));
    }

    @Test
    void shouldReturnNotFound_whenListingTasksOfMissingProject() throws Exception {
        mockMvc.perform(get("/api/projects/9999/tasks").header(HttpHeaders.AUTHORIZATION, ALICE))
                .andExpect(status().isNotFound());
    }

    /* ------------------------------- Activity trail ------------------------------ */

    @Test
    void shouldRecordCreateAction_andExposeItOnTheTrail() throws Exception {
        String body = """
                {"title":"Fresh task","description":"d","status":"TODO","priority":"HIGH","dueDate":"2026-09-01"}
                """;
        String response = mockMvc.perform(post("/api/projects/1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get("/api/tasks/" + id + "/activity").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[*].action",
                        org.hamcrest.Matchers.hasItem("CREATE")));
    }

    @Test
    void shouldRecordUpdateActions_afterEditingTask() throws Exception {
        String body = """
                {"title":"Renamed","description":"changed","status":"COMPLETED","priority":"LOW","dueDate":"2026-01-01"}
                """;
        mockMvc.perform(put("/api/tasks/2")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/2/activity").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[*].action",
                        org.hamcrest.Matchers.hasItem("UPDATE")))
                .andExpect(jsonPath("$.content[*].fieldChanged",
                        org.hamcrest.Matchers.hasItem("status")));
    }

    @Test
    void shouldForbidRegularUser_fromReadingActivityOfAnothersTask() throws Exception {
        // Task #6 is in bob's project #3, assigned to charlie — not visible to alice.
        mockMvc.perform(get("/api/tasks/6/activity").header(HttpHeaders.AUTHORIZATION, ALICE))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnNotFound_whenReadingActivityOfMissingTask() throws Exception {
        mockMvc.perform(get("/api/tasks/9999/activity").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnUnauthorized_whenReadingActivityWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/tasks/1/activity"))
                .andExpect(status().isUnauthorized());
    }
}
