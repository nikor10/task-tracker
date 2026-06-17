package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.Task;
import com.nikola.task_tracker_project_spring.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task>{

    // Custom JPQL query: overdue tasks (due before the given date and not yet completed).
    @Query("SELECT t FROM Task t " +
           "WHERE t.dueDate < :date AND t.status <> com.nikola.task_tracker_project_spring.entity.TaskStatus.COMPLETED " +
           "ORDER BY t.dueDate ASC")
    List<Task> findOverdueTasks(@Param("date") LocalDate date);

    // Same as findOverdueTasks but scoped to a single user: tasks in projects they own
    // OR tasks assigned to them ("both" visibility rule).
    @Query("SELECT t FROM Task t " +
           "WHERE t.dueDate < :date AND t.status <> com.nikola.task_tracker_project_spring.entity.TaskStatus.COMPLETED " +
           "  AND (t.project.owner.id = :userId OR t.assignee.id = :userId) " +
           "ORDER BY t.dueDate ASC")
    List<Task> findOverdueTasksForUser(@Param("date") LocalDate date, @Param("userId") Long userId);

    // Tasks due on a given date, scoped to a single user (owned project OR assigned to them).
    @Query("SELECT t FROM Task t " +
           "WHERE t.dueDate = :date AND (t.project.owner.id = :userId OR t.assignee.id = :userId)")
    List<Task> findByDueDateForUser(@Param("date") LocalDate date, @Param("userId") Long userId);
    // Generates: SELECT * FROM tasks WHERE project_id = ?1 AND status = ?2 LIMIT ?3 OFFSET ?4
    Page<Task> findByProjectIdAndStatus(Long projectId, TaskStatus status, Pageable pageable);

    // Generates: SELECT * FROM tasks WHERE project_id = ?1 LIMIT ?2 OFFSET ?3
    Page<Task> findByProjectId(Long projectId, Pageable pageable);

    List<Task> findByDueDate(LocalDate date);

    List<Task> findByAssigneeId(Long assigneeId);
}
