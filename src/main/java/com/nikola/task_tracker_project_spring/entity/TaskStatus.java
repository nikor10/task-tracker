package com.nikola.task_tracker_project_spring.entity;

// Note: springdoc inlines an enum as a plain string + value list and ignores a class-level
// @Schema description, so the meaning of each value lives on the referencing field instead
// (e.g. Task.status).
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    COMPLETED
}
