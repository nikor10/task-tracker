package com.nikola.task_tracker_project_spring.controller;

import com.nikola.task_tracker_project_spring.entity.Task;
import com.nikola.task_tracker_project_spring.entity.TaskActivity;
import com.nikola.task_tracker_project_spring.entity.TaskPriority;
import com.nikola.task_tracker_project_spring.entity.TaskStatus;
import com.nikola.task_tracker_project_spring.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;


@RestController
@RequestMapping("/api")
@Tag(name = "Tasks", description = "Create, browse, filter, and audit tasks within a project.")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @PostMapping("/projects/{projectId}/tasks")
    @Operation(summary = "Create a task in a project",
            description = "Adds a new task to the given project. The caller must own the project "
                    + "(admins may add to any). The creation is recorded in the task's activity log.")
    public Task addTask(@PathVariable Long projectId, @RequestBody @Valid Task task)
    {
        return taskService.addTask(projectId, task);
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "Get a task by id",
            description = "Returns a single task. Visible to the project owner and to the task's assignee; "
                    + "others receive 403, and a missing id returns 404.")
    public Task getTaskById(@PathVariable Long id)
    {
        return taskService.getTaskById(id);
    }

    @GetMapping("/tasks/{id}/activity")
    @Operation(summary = "Get a task's activity log",
            description = "Returns the append-only audit trail for the task — one entry per changed "
                    + "field, plus whole-entity CREATE/DELETE entries — newest first by default. "
                    + "Same visibility rules as fetching the task.")
    public Page<TaskActivity> getTaskActivity(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable)
    {
        return taskService.getTaskActivity(id, pageable);
    }

    @GetMapping("projects/{projectId}/tasks")
    @Operation(summary = "List/search tasks in a project",
            description = "Returns a paged list of the project's tasks. All filter parameters are "
                    + "optional and combined with AND; omit them to list everything. Text filters "
                    + "(title, description) match partially; date filters bound the due date. "
                    + "Only tasks the caller may see are returned.")
    public Page<Task> getAllTasks (@PathVariable Long projectId,
                                   @Parameter(description = "Partial, case-insensitive match on the task title")
                                   @RequestParam(required = false) String title,
                                   @Parameter(description = "Partial, case-insensitive match on the description")
                                   @RequestParam(required = false) String description,
                                   @Parameter(description = "Exact task status")
                                   @RequestParam(required = false) TaskStatus status,
                                   @Parameter(description = "Exact task priority")
                                   @RequestParam(required = false) TaskPriority priority,
                                   @Parameter(description = "Id of the assigned user")
                                   @RequestParam(required = false) Long assigneeId,
                                   @Parameter(description = "Only tasks due on or after this date (ISO yyyy-MM-dd)")
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueAfter,
                                   @Parameter(description = "Only tasks due on or before this date (ISO yyyy-MM-dd)")
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
                                   @PageableDefault(size = 10, sort = "dueDate") Pageable pageable)
    {
        return taskService.getAllTasks(projectId, title, description, status, priority,
                                       assigneeId, dueAfter, dueBefore, pageable);
    }

    @RequestMapping(value = "tasks/{id}", method = RequestMethod.PUT)
    @Operation(summary = "Update a task",
            description = "Replaces the editable fields of a task. Each changed field is recorded as a "
                    + "separate entry in the task's activity log. Restricted to the project owner.")
    public Task updateById(@PathVariable Long id, @RequestBody @Valid Task task)
    {
        return taskService.updateById(id, task);
    }

    @RequestMapping(value = "tasks/{id}", method = RequestMethod.DELETE)
    @Operation(summary = "Delete a task",
            description = "Deletes a task and records a DELETE entry in its activity log. "
                    + "Restricted to the project owner.")
    public void deleteById(@PathVariable Long id)
    {
        taskService.deleteById(id);
    }

    @GetMapping(value = "tasks/due-today")
    @Operation(summary = "List tasks due today",
            description = "Returns the caller's tasks whose due date is today.")
    public List<Task> getTasksDueToday ()
    {
        return taskService.getTasksDueToday();
    }

    @GetMapping(value = "users/{userId}/tasks")
    @Operation(summary = "List tasks assigned to a user",
            description = "Returns every task currently assigned to the given user.")
    public List<Task> getTasksByUser (@PathVariable Long userId)
    {
        return taskService.getTasksByUser(userId);
    }

    @GetMapping(value = "tasks/overdue")
    @Operation(summary = "List overdue tasks",
            description = "Returns the caller's incomplete tasks whose due date is in the past.")
    public List<Task> getOverdueTasks ()
    {
        return taskService.getOverdueTasks();
    }
}