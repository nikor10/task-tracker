package com.nikola.task_tracker_project_spring.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One entry in a task's audit trail: a single field change (or a whole-entity CREATE/DELETE),
 * recording who did it, when, and the before/after values.
 *
 * <p>Append-only by design: there are no public setters and the application never updates or
 * deletes a persisted row. References to the task and actor are kept as plain ids plus
 * human-readable snapshots (no JPA associations), so a row stays meaningful even after the
 * task or user it describes is deleted.
 */
@Entity
@Table(name = "task_activity")
@Schema(description = "A single, immutable audit-trail entry describing one change to a task. "
        + "Read-only: produced by the server, never accepted in a request body.")
public class TaskActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Server-generated identifier", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Column(nullable = false)
    @Schema(description = "Id of the task this entry describes", example = "42")
    private Long taskId;

    // Snapshot of the task title at write time, so the row reads sensibly after the task is gone.
    @Column(nullable = false)
    @Schema(description = "Task title captured at write time, so the entry still reads sensibly "
            + "after the task is deleted", example = "Write API docs")
    private String taskTitle;

    // The actor. Nullable to allow a future system/non-user action.
    @Schema(description = "Id of the user who made the change; null for a system action", example = "3")
    private Long userId;

    // Snapshot of the actor's username at write time.
    @Schema(description = "Username of the actor captured at write time", example = "alice")
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "Kind of change recorded: CREATE (task added), UPDATE (a field changed), "
            + "or DELETE (task removed)", example = "UPDATE")
    private TaskActivityAction action;

    // Which field changed; null for a whole-entity CREATE or DELETE.
    @Schema(description = "Name of the field that changed; null for a whole-entity CREATE or DELETE",
            example = "status")
    private String fieldChanged;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Field value before the change; null when not applicable", example = "TODO")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Field value after the change; null when not applicable", example = "IN_PROGRESS")
    private String newValue;

    // Groups the rows produced by a single atomic edit (e.g. one update touching three fields).
    @Schema(description = "Identifier shared by all entries produced by a single atomic edit, so a "
            + "multi-field update can be grouped back together")
    private String changeSetId;

    @Column(nullable = false, updatable = false)
    @Schema(description = "When the change was recorded", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    protected TaskActivity() {
        // for JPA
    }

    public TaskActivity(Long taskId, String taskTitle, Long userId, String username,
                        TaskActivityAction action, String fieldChanged,
                        String oldValue, String newValue, String changeSetId) {
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.fieldChanged = fieldChanged;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changeSetId = changeSetId;
    }

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public String getTaskTitle() { return taskTitle; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public TaskActivityAction getAction() { return action; }
    public String getFieldChanged() { return fieldChanged; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getChangeSetId() { return changeSetId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
