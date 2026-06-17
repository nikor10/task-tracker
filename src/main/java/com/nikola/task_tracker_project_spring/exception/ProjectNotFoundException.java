package com.nikola.task_tracker_project_spring.exception;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(Long id) {
        super("Project with id " + id + " not found");
    }
}
