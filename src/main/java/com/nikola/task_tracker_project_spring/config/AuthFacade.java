package com.nikola.task_tracker_project_spring.config;

import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Thin helper around the Spring Security context so services can scope data to the
 * signed-in user. The "admin" account is in-memory (ROLE_ADMIN) and has no row in the
 * users table, so {@link #currentUser()} / {@link #currentUserId()} return null for it.
 */
@Component
public class AuthFacade {

    private final UserRepository userRepository;

    public AuthFacade(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public String username() {
        Authentication auth = authentication();
        return auth != null ? auth.getName() : null;
    }

    public boolean isAdmin() {
        Authentication auth = authentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** The signed-in domain user, or null for the in-memory admin. */
    public User currentUser() {
        if (isAdmin()) {
            return null;
        }
        return userRepository.findByUsername(username())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
    }

    /** The signed-in domain user's id, or null for the in-memory admin. */
    public Long currentUserId() {
        User user = currentUser();
        return user != null ? user.getId() : null;
    }
}
