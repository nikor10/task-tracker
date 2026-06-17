package com.nikola.task_tracker_project_spring.entity;

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
public class TaskActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    // Snapshot of the task title at write time, so the row reads sensibly after the task is gone.
    @Column(nullable = false)
    private String taskTitle;

    // The actor. Nullable to allow a future system/non-user action.
    private Long userId;

    // Snapshot of the actor's username at write time.
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskActivityAction action;

    // Which field changed; null for a whole-entity CREATE or DELETE.
    private String fieldChanged;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    // Groups the rows produced by a single atomic edit (e.g. one update touching three fields).
    private String changeSetId;

    @Column(nullable = false, updatable = false)
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
