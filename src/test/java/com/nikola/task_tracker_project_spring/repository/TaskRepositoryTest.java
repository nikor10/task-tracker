package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Flyway disabled so the schema is Hibernate-generated and tests own their data.
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TaskRepositoryTest {

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

    private Project persistProject(User owner) {
        Project p = new Project();
        p.setName("Project");
        p.setOwner(owner);
        return entityManager.persist(p);
    }

    private Task persistTask(String title, TaskStatus status, LocalDate dueDate,
                             Project project, User assignee) {
        Task t = new Task();
        t.setTitle(title);
        t.setStatus(status);
        t.setPriority(TaskPriority.MEDIUM);
        t.setDueDate(dueDate);
        t.setProject(project);
        t.setAssignee(assignee);
        return entityManager.persist(t);
    }

    @Test
    void shouldSaveFindAndDelete() {
        User owner = persistUser("alice");
        Project project = persistProject(owner);

        Task saved = persistTask("Task", TaskStatus.TODO, LocalDate.now(), project, owner);
        entityManager.flush();

        assertThat(taskRepository.findById(saved.getId())).isPresent();

        taskRepository.deleteById(saved.getId());
        assertThat(taskRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void shouldReturnOverdueTasks_excludingCompletedAndFutureDated() {
        User owner = persistUser("alice");
        Project project = persistProject(owner);
        LocalDate today = LocalDate.now();

        persistTask("Overdue", TaskStatus.TODO, today.minusDays(1), project, owner);
        persistTask("Overdue done", TaskStatus.COMPLETED, today.minusDays(1), project, owner);
        persistTask("Future", TaskStatus.TODO, today.plusDays(1), project, owner);
        entityManager.flush();

        List<Task> overdue = taskRepository.findOverdueTasks(today);

        assertThat(overdue).extracting(Task::getTitle).containsExactly("Overdue");
    }

    @Test
    void shouldFilterByProjectAndStatus() {
        User owner = persistUser("alice");
        Project project = persistProject(owner);
        persistTask("Todo task", TaskStatus.TODO, LocalDate.now(), project, owner);
        persistTask("Done task", TaskStatus.COMPLETED, LocalDate.now(), project, owner);
        entityManager.flush();

        Page<Task> todo = taskRepository.findByProjectIdAndStatus(
                project.getId(), TaskStatus.TODO, PageRequest.of(0, 10));

        assertThat(todo.getContent()).extracting(Task::getTitle).containsExactly("Todo task");
    }

    @Test
    void shouldPageByProject() {
        User owner = persistUser("alice");
        Project project = persistProject(owner);
        persistTask("Task one", TaskStatus.TODO, LocalDate.now(), project, owner);
        persistTask("Task two", TaskStatus.TODO, LocalDate.now(), project, owner);
        persistTask("Task three", TaskStatus.TODO, LocalDate.now(), project, owner);
        entityManager.flush();

        Page<Task> firstPage = taskRepository.findByProjectId(project.getId(), PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
    }

    @Test
    void shouldFindByDueDate() {
        User owner = persistUser("alice");
        Project project = persistProject(owner);
        LocalDate due = LocalDate.of(2026, 6, 20);
        persistTask("Due", TaskStatus.TODO, due, project, owner);
        persistTask("Other", TaskStatus.TODO, due.plusDays(5), project, owner);
        entityManager.flush();

        assertThat(taskRepository.findByDueDate(due)).extracting(Task::getTitle).containsExactly("Due");
    }

    @Test
    void shouldFindByAssignee() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");
        Project project = persistProject(alice);
        persistTask("Alice task", TaskStatus.TODO, LocalDate.now(), project, alice);
        persistTask("Bob task", TaskStatus.TODO, LocalDate.now(), project, bob);
        entityManager.flush();

        assertThat(taskRepository.findByAssigneeId(bob.getId()))
                .extracting(Task::getTitle).containsExactly("Bob task");
    }

    @Test
    void shouldReturnOverdueTasksForUser_byOwnedProjectOrAssignment() {
        User alice = persistUser("alice");   // owns the project
        User bob = persistUser("bob");       // only assigned to one task
        User carol = persistUser("carol");   // unrelated
        Project project = persistProject(alice);
        LocalDate today = LocalDate.now();

        persistTask("Owned overdue", TaskStatus.TODO, today.minusDays(1), project, carol);
        persistTask("Assigned overdue", TaskStatus.TODO, today.minusDays(2), project, bob);
        persistTask("Owned but completed", TaskStatus.COMPLETED, today.minusDays(1), project, alice);
        entityManager.flush();

        // alice sees both overdue tasks because she owns the project.
        assertThat(taskRepository.findOverdueTasksForUser(today, alice.getId()))
                .extracting(Task::getTitle)
                .containsExactly("Assigned overdue", "Owned overdue"); // sorted by dueDate asc

        // bob sees only the task assigned to him.
        assertThat(taskRepository.findOverdueTasksForUser(today, bob.getId()))
                .extracting(Task::getTitle)
                .containsExactly("Assigned overdue");
    }

    @Test
    void shouldFindByDueDateForUser_byOwnedProjectOrAssignment() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");
        Project aliceProject = persistProject(alice);
        Project bobProject = persistProject(bob);
        LocalDate due = LocalDate.of(2026, 6, 20);

        persistTask("In alice project", TaskStatus.TODO, due, aliceProject, bob);
        persistTask("Assigned to alice", TaskStatus.TODO, due, bobProject, alice);
        persistTask("Neither", TaskStatus.TODO, due, bobProject, bob);
        entityManager.flush();

        assertThat(taskRepository.findByDueDateForUser(due, alice.getId()))
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrder("In alice project", "Assigned to alice");
    }
}
