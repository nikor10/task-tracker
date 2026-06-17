package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.TaskActivity;
import com.nikola.task_tracker_project_spring.entity.TaskActivityAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

// Flyway disabled so the schema is Hibernate-generated and the test owns its data.
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TaskActivityRepositoryTest {

    @Autowired
    private TaskActivityRepository activityRepository;

    @Autowired
    private TestEntityManager entityManager;

    private TaskActivity entry(Long taskId, String taskTitle) {
        return new TaskActivity(taskId, taskTitle, 5L, "alice",
                TaskActivityAction.UPDATE, "status", "TODO", "COMPLETED", "cs-1");
    }

    @Test
    void findByTaskId_returnsOnlyThatTasksEntries() {
        entityManager.persist(entry(1L, "Task one"));
        entityManager.persist(entry(1L, "Task one"));
        entityManager.persist(entry(2L, "Task two"));
        entityManager.flush();

        Page<TaskActivity> page = activityRepository.findByTaskId(1L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(TaskActivity::getTaskId).containsOnly(1L);
    }

    @Test
    void findByTaskId_paginates() {
        entityManager.persist(entry(1L, "T"));
        entityManager.persist(entry(1L, "T"));
        entityManager.persist(entry(1L, "T"));
        entityManager.flush();

        Page<TaskActivity> firstPage = activityRepository.findByTaskId(1L, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findByTaskId_returnsEmptyPage_whenNoActivity() {
        Page<TaskActivity> page = activityRepository.findByTaskId(999L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }
}
