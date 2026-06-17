package com.nikola.task_tracker_project_spring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Authentication", description = "Identify the currently signed-in user.")
public class AuthController {

    // Identifies the signed-in user to the frontend (username + whether they are admin).
    // Requires authentication (covered by the /api/** rule), so anonymous callers get 401.
    @GetMapping("/api/me")
    @Operation(summary = "Get the current user",
            description = "Returns the authenticated caller's username and whether they hold the admin "
                    + "role. Anonymous callers receive 401, so the frontend uses this to confirm login.")
    public Map<String, Object> me(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return Map.of(
                "username", authentication.getName(),
                "admin", admin
        );
    }
}
