package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.Project;
import com.nikola.task_tracker_project_spring.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Flyway disabled so the schema is Hibernate-generated and tests own their data
// (Flyway's seed would otherwise collide with the users persisted here).
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

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
        p.setDescription("desc");
        p.setOwner(owner);
        return entityManager.persist(p);
    }

    @Test
    void shouldSaveAndFindById() {
        User owner = persistUser("alice");
        Project saved = projectRepository.save(makeProject("Website", owner));

        assertThat(saved.getId()).isNotNull();
        assertThat(projectRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void shouldDeleteProject() {
        User owner = persistUser("alice");
        Project saved = projectRepository.save(makeProject("Temp", owner));

        projectRepository.deleteById(saved.getId());

        assertThat(projectRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void shouldReturnOnlyProjectsForGivenOwner() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");
        persistProject("Website", alice);
        persistProject("Mobile App", alice);
        persistProject("Data Pipeline", bob);
        entityManager.flush();

        List<Project> aliceProjects = projectRepository.findProjectsByOwnerId(alice.getId());

        assertThat(aliceProjects)
                .hasSize(2)
                .extracting(Project::getName)
                .containsExactlyInAnyOrder("Website", "Mobile App");
    }

    @Test
    void shouldReturnEmpty_whenOwnerHasNoProjects() {
        User lonely = persistUser("lonely");

        assertThat(projectRepository.findProjectsByOwnerId(lonely.getId())).isEmpty();
    }

    @Test
    void shouldPageProjectsByOwner() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");
        persistProject("Website", alice);
        persistProject("Mobile App", alice);
        persistProject("Data Pipeline", bob);
        entityManager.flush();

        Page<Project> firstPage = projectRepository.findByOwnerId(alice.getId(), PageRequest.of(0, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getContent().get(0).getOwner().getId()).isEqualTo(alice.getId());
    }

    private Project makeProject(String name, User owner) {
        Project p = new Project();
        p.setName(name);
        p.setDescription("desc");
        p.setOwner(owner);
        return p;
    }
}
