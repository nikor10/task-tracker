package com.nikola.task_tracker_project_spring.config;

import com.nikola.task_tracker_project_spring.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public: H2 console + the static SPA shell (login page must load unauthenticated).
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/", "/index.html", "/app.js", "/favicon.ico").permitAll()
                // Registering a new user is an admin-only action.
                .requestMatchers(HttpMethod.POST, "/api/users").hasRole("ADMIN")
                // Everything else under the API requires a logged-in user.
                .requestMatchers("/api/**").authenticated()
                .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                ).permitAll()
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            // Return a plain JSON 401 instead of "WWW-Authenticate: Basic", so the browser
            // never shows its native credential popup — the SPA handles login itself.
            .httpBasic(basic -> basic.authenticationEntryPoint(restAuthenticationEntryPoint()));

        return http.build();
    }

    private AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"message\":\"Authentication required\"}");
        };
    }

    /**
     * DB-backed authentication. The single in-memory "admin" account keeps ROLE_ADMIN;
     * every other username is looked up in the users table and granted ROLE_USER, so
     * alice/bob/charlie and any user created via POST /api/users can sign in.
     */
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository, PasswordEncoder encoder) {
        final String adminPassword = encoder.encode("admin");
        return username -> {
            if ("admin".equalsIgnoreCase(username)) {
                return User.withUsername("admin").password(adminPassword).roles("ADMIN").build();
            }
            com.nikola.task_tracker_project_spring.entity.User account = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            return User.withUsername(account.getUsername())
                .password(account.getPassword())
                .roles("USER")
                .build();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
