package com.nikola.task_tracker_project_spring.controller;

import com.nikola.task_tracker_project_spring.entity.Task;
import com.nikola.task_tracker_project_spring.entity.TaskActivity;
import com.nikola.task_tracker_project_spring.entity.TaskPriority;
import com.nikola.task_tracker_project_spring.entity.TaskStatus;
import com.nikola.task_tracker_project_spring.service.TaskService;
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
public class TaskController {

    @Autowired
    private TaskService taskService;

    @PostMapping("/projects/{projectId}/tasks")
    public Task addTask(@PathVariable Long projectId, @RequestBody @Valid Task task)
    {
        return taskService.addTask(projectId, task);
    }

    @GetMapping("/tasks/{id}")
    public Task getTaskById(@PathVariable Long id)
    {
        return taskService.getTaskById(id);
    }

    @GetMapping("/tasks/{id}/activity")
    public Page<TaskActivity> getTaskActivity(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable)
    {
        return taskService.getTaskActivity(id, pageable);
    }

    @GetMapping("projects/{projectId}/tasks")
    public Page<Task> getAllTasks (@PathVariable Long projectId,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(required = false) TaskStatus status,
                                   @RequestParam(required = false) TaskPriority priority,
                                   @RequestParam(required = false) Long assigneeId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueAfter,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
                                   @PageableDefault(size = 10, sort = "dueDate") Pageable pageable)
    {
        return taskService.getAllTasks(projectId, title, description, status, priority,
                                       assigneeId, dueAfter, dueBefore, pageable);
    }

    @RequestMapping(value = "tasks/{id}", method = RequestMethod.PUT)
    public Task updateById(@PathVariable Long id, @RequestBody @Valid Task task)
    {
        return taskService.updateById(id, task);
    }

    @RequestMapping(value = "tasks/{id}", method = RequestMethod.DELETE)
    public void deleteById(@PathVariable Long id)
    {
        taskService.deleteById(id);
    }

    @GetMapping(value = "tasks/due-today")
    public List<Task> getTasksDueToday ()
    {
        return taskService.getTasksDueToday();
    }

    @GetMapping(value = "users/{userId}/tasks")
    public List<Task> getTasksByUser (@PathVariable Long userId)
    {
        return taskService.getTasksByUser(userId);
    }

    @GetMapping(value = "tasks/overdue")
    public List<Task> getOverdueTasks ()
    {
        return taskService.getOverdueTasks();
    }
}