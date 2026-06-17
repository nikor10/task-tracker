package com.nikola.task_tracker_project_spring.event;

/**
 * Published when a task is created with an assignee. Carries only plain values
 * (not JPA entities) so the asynchronous listener can run after the transaction
 * commits without risking a LazyInitializationException.
 */
public record TaskAssignedEvent(
        Long taskId,
        String taskTitle,
        String projectName,
        String assigneeUsername,
        String assigneeEmail
) {
}
