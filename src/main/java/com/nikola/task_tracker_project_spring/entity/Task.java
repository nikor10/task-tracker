package com.nikola.task_tracker_project_spring.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Schema(description = "A unit of work belonging to a project, optionally assigned to a user.")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Server-generated identifier", accessMode = Schema.AccessMode.READ_ONLY, example = "42")
    private Long id;

    @NotBlank
    @Size(min = 3, max = 100)
    @Column(nullable = false)
    @Schema(description = "Short title of the task (3-100 characters)", example = "Write API docs")
    private String title;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Optional free-text details", example = "Document every endpoint in Swagger.")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "Current workflow status: TODO (not started), IN_PROGRESS (being worked on), "
            + "or COMPLETED (finished)", example = "TODO")
    private TaskStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "Importance of the task: LOW, MEDIUM, or HIGH", example = "HIGH")
    private TaskPriority priority;

    @Schema(description = "Date the task is due (ISO yyyy-MM-dd); may be null", example = "2026-07-01")
    private LocalDate dueDate;

    @Column(nullable = false, updatable = false)
    @Schema(description = "When the task was created", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    @Schema(description = "User the task is assigned to; may be null when unassigned")
    private User assignee;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public User getAssignee() { return assignee; }
    public void setAssignee(User assignee) { this.assignee = assignee; }
}
