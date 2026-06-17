package com.nikola.task_tracker_project_spring.service;

import com.nikola.task_tracker_project_spring.config.AuthFacade;
import com.nikola.task_tracker_project_spring.entity.Task;
import com.nikola.task_tracker_project_spring.entity.TaskActivity;
import com.nikola.task_tracker_project_spring.entity.TaskActivityAction;
import com.nikola.task_tracker_project_spring.repository.TaskActivityRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds and persists task audit-trail entries. Kept separate from TaskService so the
 * diff/stringify logic is cohesive and independently testable.
 *
 * <p>No transaction management of its own: it is always called from within a {@code @Transactional}
 * TaskService method, so the audit rows commit (or roll back) atomically with the task change.
 * The actor is read here on the request thread, where the SecurityContext is available.
 */
@Service
public class TaskActivityRecorder {

    private final TaskActivityRepository activityRepository;
    private final AuthFacade authFacade;

    public TaskActivityRecorder(TaskActivityRepository activityRepository, AuthFacade authFacade) {
        this.activityRepository = activityRepository;
        this.authFacade = authFacade;
    }

    public void recordCreate(Task task) {
        activityRepository.save(row(task.getId(), task.getTitle(), TaskActivityAction.CREATE,
                null, null, null, newChangeSetId()));
    }

    /**
     * Records one row per field that actually changed. {@code before} must still hold the old
     * values (call this BEFORE the entity's setters overwrite them); {@code after} holds the new.
     */
    public void recordUpdate(Task before, Task after) {
        Long taskId = before.getId();
        String title = after.getTitle();
        String changeSetId = newChangeSetId();

        List<TaskActivity> rows = new ArrayList<>();
        addIfChanged(rows, taskId, title, changeSetId, "title", before.getTitle(), after.getTitle());
        addIfChanged(rows, taskId, title, changeSetId, "description", before.getDescription(), after.getDescription());
        addIfChanged(rows, taskId, title, changeSetId, "status", str(before.getStatus()), str(after.getStatus()));
        addIfChanged(rows, taskId, title, changeSetId, "priority", str(before.getPriority()), str(after.getPriority()));
        addIfChanged(rows, taskId, title, changeSetId, "dueDate", str(before.getDueDate()), str(after.getDueDate()));
        addIfChanged(rows, taskId, title, changeSetId, "assignee", assigneeId(before), assigneeId(after));

        // saveAll([]) is a no-op, so a no-op update produces no audit rows.
        activityRepository.saveAll(rows);
    }

    public void recordDelete(Task task) {
        activityRepository.save(row(task.getId(), task.getTitle(), TaskActivityAction.DELETE,
                null, null, null, newChangeSetId()));
    }

    private void addIfChanged(List<TaskActivity> rows, Long taskId, String title, String changeSetId,
                              String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            rows.add(row(taskId, title, TaskActivityAction.UPDATE, field, oldValue, newValue, changeSetId));
        }
    }

    private TaskActivity row(Long taskId, String taskTitle, TaskActivityAction action,
                             String field, String oldValue, String newValue, String changeSetId) {
        return new TaskActivity(
                taskId,
                taskTitle,
                authFacade.currentUserId(),   // null for the in-memory admin (no users row)
                authFacade.username(),        // "admin" or the real username — the snapshot of who
                action, field, oldValue, newValue, changeSetId);
    }

    private static String newChangeSetId() {
        return UUID.randomUUID().toString();
    }

    // Uniform stringification: enums -> name, LocalDate -> ISO, null stays null.
    private static String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private static String assigneeId(Task task) {
        return task.getAssignee() != null && task.getAssignee().getId() != null
                ? task.getAssignee().getId().toString()
                : null;
    }
}
