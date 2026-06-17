package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Custom JPQL query: fetch all projects owned by a given user, newest first.
    @Query("SELECT p FROM Project p WHERE p.owner.id = :ownerId ORDER BY p.createdAt DESC")
    List<Project> findProjectsByOwnerId(@Param("ownerId") Long ownerId);

    // Paged variant used to scope the project list to the signed-in (non-admin) user.
    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);

    // Projects a user can browse tasks in: ones they own OR ones containing a task assigned to
    // them. Mirrors the task "visibleTo" rule (owner OR assignee) at the project level, so the
    // Tasks-tab dropdown lists every project where the user has at least one visible task.
    @Query("SELECT DISTINCT p FROM Project p WHERE p.owner.id = :userId " +
           "OR p.id IN (SELECT t.project.id FROM Task t WHERE t.assignee.id = :userId) " +
           "ORDER BY p.id")
    List<Project> findAccessibleProjects(@Param("userId") Long userId);
}
