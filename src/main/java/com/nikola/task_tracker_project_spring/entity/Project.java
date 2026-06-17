package com.nikola.task_tracker_project_spring.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Schema(description = "A container that owns a set of tasks and belongs to a single user.")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Server-generated identifier", accessMode = Schema.AccessMode.READ_ONLY, example = "7")
    private Long id;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(nullable = false)
    @Schema(description = "Project name (3-50 characters)", example = "Website Redesign")
    private String name;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Optional free-text description", example = "Q3 marketing site refresh.")
    private String description;

    @Column(nullable = false, updatable = false)
    @Schema(description = "When the project was created", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @Schema(description = "Owner of the project; set by the server to the authenticated caller on create",
            accessMode = Schema.AccessMode.READ_ONLY)
    private User owner;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Schema(description = "Tasks belonging to this project", accessMode = Schema.AccessMode.READ_ONLY)
    private List<Task> tasks = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) { this.tasks = tasks; }
}
