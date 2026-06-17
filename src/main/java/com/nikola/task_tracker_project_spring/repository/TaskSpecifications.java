package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.Task;
import com.nikola.task_tracker_project_spring.entity.TaskPriority;
import com.nikola.task_tracker_project_spring.entity.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class TaskSpecifications {

    public static Specification<Task> belongsToProject(Long projectId) {
        return (root, query, builder) -> builder.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Task> titleContains(String keyword) {
        return (root, query, builder) ->
                builder.like(builder.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Task> descriptionContains(String keyword) {
        return (root, query, builder) ->
                builder.like(builder.lower(root.get("description")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(TaskPriority priority) {
        return (root, query, builder) -> builder.equal(root.get("priority"), priority);
    }

    public static Specification<Task> assignedTo(Long assigneeId) {
        return (root, query, builder) -> builder.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<Task> dueOn(LocalDate date) {
        return (root, query, builder) -> builder.equal(root.get("dueDate"), date);
    }

    public static Specification<Task> dueBefore(LocalDate date) {
        return (root, query, builder) -> builder.lessThan(root.get("dueDate"), date);
    }

    public static Specification<Task> dueAfter(LocalDate date) {
        return (root, query, builder) -> builder.greaterThan(root.get("dueDate"), date);
    }

    public static Specification<Task> visibleTo(Long userId) {
        return (root, query, builder) -> builder.or(
                builder.equal(root.get("project").get("owner").get("id"), userId),
                builder.equal(root.get("assignee").get("id"), userId));
    }
}
