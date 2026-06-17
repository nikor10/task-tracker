package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Flyway is disabled so the schema comes from Hibernate (create-drop) and each test
// controls its own data — otherwise Flyway's seed (alice/bob/charlie) collides with it.
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword("password123");
        return u;
    }

    @BeforeEach
    void seed() {
        entityManager.persist(user("alice", "alice@example.com"));
        entityManager.persist(user("bob", "bob@work.org"));
        entityManager.flush();
    }

    @Test
    void shouldSaveAndFindById() {
        User saved = userRepository.save(user("charlie", "charlie@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(userRepository.findById(saved.getId())).isPresent();
        assertThat(saved.getCreatedAt()).isNotNull(); // set via @PrePersist
    }

    @Test
    void shouldFindAll() {
        assertThat(userRepository.findAll()).hasSize(2);
    }

    @Test
    void shouldDeleteUser() {
        User u = userRepository.save(user("dora", "dora@example.com"));
        userRepository.deleteById(u.getId());

        assertThat(userRepository.findById(u.getId())).isEmpty();
    }

    @Test
    void shouldMatchByUsernameFragment() {
        List<User> result = userRepository.searchByUsernameOrEmail("ali");

        assertThat(result).extracting(User::getUsername).containsExactly("alice");
    }

    @Test
    void shouldMatchByEmailFragment() {
        List<User> result = userRepository.searchByUsernameOrEmail("work.org");

        assertThat(result).extracting(User::getUsername).containsExactly("bob");
    }

    @Test
    void shouldMatchCaseInsensitively() {
        assertThat(userRepository.searchByUsernameOrEmail("ALICE"))
                .extracting(User::getUsername).containsExactly("alice");
    }

    @Test
    void shouldReturnAll_whenKeywordEmpty() {
        assertThat(userRepository.searchByUsernameOrEmail("")).hasSize(2);
    }

    @Test
    void shouldReturnEmpty_whenNoMatch() {
        assertThat(userRepository.searchByUsernameOrEmail("zzz")).isEmpty();
    }

    @Test
    void shouldFindByUsername_whenPresent() {
        assertThat(userRepository.findByUsername("alice"))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("alice@example.com");
    }

    @Test
    void shouldReturnEmpty_whenUsernameUnknown() {
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }
}
