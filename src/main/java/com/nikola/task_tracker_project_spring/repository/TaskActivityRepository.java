package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.TaskActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {

    // The trail for one task, paginated. Sort order (e.g. newest-first) comes from the Pageable.
    Page<TaskActivity> findByTaskId(Long taskId, Pageable pageable);
}
