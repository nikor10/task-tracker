package com.nikola.task_tracker_project_spring.repository;

import com.nikola.task_tracker_project_spring.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Custom JPQL query: case-insensitive search of users by username or email.
    @Query("SELECT u FROM User u " +
           "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> searchByUsernameOrEmail(@Param("keyword") String keyword);

    // Used by the DB-backed UserDetailsService to authenticate by username.
    Optional<User> findByUsername(String username);
}
