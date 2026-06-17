package com.nikola.task_tracker_project_spring.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    // Identifies the signed-in user to the frontend (username + whether they are admin).
    // Requires authentication (covered by the /api/** rule), so anonymous callers get 401.
    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return Map.of(
                "username", authentication.getName(),
                "admin", admin
        );
    }
}
