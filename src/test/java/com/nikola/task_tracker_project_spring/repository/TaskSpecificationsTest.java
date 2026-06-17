package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

import static com.nikola.task_tracker_project_spring.repository.TaskSpecifications.*;
import static org.assertj.core.api.Assertions.assertThat;

// Flyway disabled so the schema is Hibernate-generated and tests own their data.
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TaskSpecificationsTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setPassword("password123");
        return entityManager.persist(u);
    }

    private Project persistProject(String name, User owner) {
        Project p = new Project();
        p.setName(name);
        p.setOwner(owner);
        return entityManager.persist(p);
    }

    private Task persistTask(String title, String description, TaskStatus status, TaskPriority priority,
                             LocalDate dueDate, Project project, User assignee) {
        Task t = new Task();
        t.setTitle(title);
        t.setDescription(description);
        t.setStatus(status);
        t.setPriority(priority);
        t.setDueDate(dueDate);
        t.setProject(project);
        t.setAssignee(assignee);
        return entityManager.persist(t);
    }

    @Test
    void belongsToProject_returnsOnlyTasksInThatProject() {
        User alice = persistUser("alice");
        Project projectA = persistProject("Project A", alice);
        Project projectB = persistProject("Project B", alice);
        persistTask("In A", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), projectA, alice);
        persistTask("In B", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), projectB, alice);
        entityManager.flush();

        List<Task> result = taskRepository.findAll(belongsToProject(projectA.getId()));

        assertThat(result).extracting(Task::getTitle).containsExactly("In A");
    }

    @Test
    void titleContains_matchesCaseInsensitiveSubstring() {
        User alice = persistUser("alice");
        Project project = persistProject("Project", alice);
        persistTask("Fix Login Bug", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        persistTask("Write docs", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        entityManager.flush();

        List<Task> result = taskRepository.findAll(titleContains("login"));

        assertThat(result).extracting(Task::getTitle).containsExactly("Fix Login Bug");
    }

    @Test
    void descriptionContains_matchesCaseInsensitiveSubstring() {
        User alice = persistUser("alice");
        Project project = persistProject("Project", alice);
        persistTask("Task one", "Refactor the SCHEDULER", TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        persistTask("Task two", "Unrelated notes", TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        entityManager.flush();

        List<Task> result = taskRepository.findAll(descriptionContains("scheduler"));

        assertThat(result).extracting(Task::getTitle).containsExactly("Task one");
    }

    @Test
    void hasStatus_filtersByStatus() {
        User alice = persistUser("alice");
        Project project = persistProject("Project", alice);
        persistTask("Todo task", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        persistTask("Done task", null, TaskStatus.COMPLETED, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        entityManager.flush();

        List<Task> result = taskRepository.findAll(hasStatus(TaskStatus.COMPLETED));

        assertThat(result).extracting(Task::getTitle).containsExactly("Done task");
    }

    @Test
    void hasPriority_filtersByPriority() {
        User alice = persistUser("alice");
        Project project = persistProject("Project", alice);
        persistTask("High one", null, TaskStatus.TODO, TaskPriority.HIGH, LocalDate.now(), project, alice);
        persistTask("Low one", null, TaskStatus.TODO, TaskPriority.LOW, LocalDate.now(), project, alice);
        entityManager.flush();

        List<Task> result = taskRepository.findAll(hasPriority(TaskPriority.HIGH));

        assertThat(result).extracting(Task::getTitle).containsExactly("High one");
    }

    @Test
    void assignedTo_filtersByAssignee() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");
        Project project = persistProject("Project", alice);
        persistTask("Alice task", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        persistTask("Bob task", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, bob);
        entityManager.flush();

        List<Task> result = taskRepository.findAll(assignedTo(bob.getId()));

        assertThat(result).extracting(Task::getTitle).containsExactly("Bob task");
    }

    @Test
    void dueAfterAndDueBefore_areExclusiveOfTheBoundaryDate() {
        User alice = persistUser("alice");
        Project project = persistProject("Project", alice);
        LocalDate pivot = LocalDate.of(2026, 6, 20);
        persistTask("Before", null, TaskStatus.TODO, TaskPriority.MEDIUM, pivot.minusDays(1), project, alice);
        persistTask("On pivot", null, TaskStatus.TODO, TaskPriority.MEDIUM, pivot, project, alice);
        persistTask("After", null, TaskStatus.TODO, TaskPriority.MEDIUM, pivot.plusDays(1), project, alice);
        entityManager.flush();

        // Strict comparisons: the pivot date itself is excluded by both.
        assertThat(taskRepository.findAll(dueAfter(pivot)))
                .extracting(Task::getTitle).containsExactly("After");
        assertThat(taskRepository.findAll(dueBefore(pivot)))
                .extracting(Task::getTitle).containsExactly("Before");
    }

    @Test
    void combinedSpecifications_areAndedTogether() {
        User alice = persistUser("alice");
        Project project = persistProject("Project", alice);
        Project otherProject = persistProject("Other", alice);
        // The one task matching every criterion.
        persistTask("Match", null, TaskStatus.TODO, TaskPriority.HIGH, LocalDate.now(), project, alice);
        // Each of these fails exactly one criterion.
        persistTask("Wrong status", null, TaskStatus.COMPLETED, TaskPriority.HIGH, LocalDate.now(), project, alice);
        persistTask("Wrong priority", null, TaskStatus.TODO, TaskPriority.LOW, LocalDate.now(), project, alice);
        persistTask("Wrong project", null, TaskStatus.TODO, TaskPriority.HIGH, LocalDate.now(), otherProject, alice);
        entityManager.flush();

        Specification<Task> spec = Specification.where(belongsToProject(project.getId()))
                .and(hasStatus(TaskStatus.TODO))
                .and(hasPriority(TaskPriority.HIGH));

        assertThat(taskRepository.findAll(spec))
                .extracting(Task::getTitle).containsExactly("Match");
    }

    @Test
    void visibleTo_matchesProjectOwnerOrAssignee() {
        User alice = persistUser("alice");   // owns the project
        User bob = persistUser("bob");        // assigned to one task
        User carol = persistUser("carol");    // neither owner nor assignee
        Project project = persistProject("Project", alice);
        persistTask("Task one", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        persistTask("Task two", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, bob);
        entityManager.flush();

        // alice owns the project, so the owner clause matches every task in it.
        assertThat(taskRepository.findAll(visibleTo(alice.getId())))
                .extracting(Task::getTitle).containsExactlyInAnyOrder("Task one", "Task two");
        // bob is not the owner, so he sees only the task assigned to him.
        assertThat(taskRepository.findAll(visibleTo(bob.getId())))
                .extracting(Task::getTitle).containsExactly("Task two");
        // carol owns nothing and is assigned nothing.
        assertThat(taskRepository.findAll(visibleTo(carol.getId()))).isEmpty();
    }

    @Test
    void specification_worksWithPagination() {
        User alice = persistUser("alice");
        Project project = persistProject("Project", alice);
        persistTask("One", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        persistTask("Two", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        persistTask("Three", null, TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.now(), project, alice);
        entityManager.flush();

        Page<Task> firstPage = taskRepository.findAll(belongsToProject(project.getId()), PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
    }
}
