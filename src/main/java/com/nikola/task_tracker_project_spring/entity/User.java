package com.nikola.task_tracker_project_spring.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Schema(description = "An account that can own projects and be assigned tasks.")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Server-generated identifier", accessMode = Schema.AccessMode.READ_ONLY, example = "3")
    private Long id;

    @NotBlank
    @Size(min = 3)
    @Column(nullable = false, unique = true)
    @Schema(description = "Unique login name (min 3 characters)", example = "alice")
    private String username;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    @Schema(description = "Unique email address", example = "alice@example.com")
    private String email;

    @NotBlank
    @Size(min = 8)
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Login password (min 8 characters); write-only, never returned in responses",
            accessMode = Schema.AccessMode.WRITE_ONLY, example = "s3cretpw")
    private String password;

    @Column(nullable = false, updatable = false)
    @Schema(description = "When the account was created", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
