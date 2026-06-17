package com.nikola.task_tracker_project_spring.controller;

import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Register and look up the accounts used to own projects and receive task assignments.")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    @Operation(summary = "Register a new user",
            description = "Creates a user account. Admin-only. The password is write-only and is "
                    + "never returned in responses.")
    public User createUser(@RequestBody @Valid User user) {
        return userService.createUser(user);
    }

    @GetMapping
    @Operation(summary = "List all users",
            description = "Returns every registered user (passwords omitted).")
    public List<User> getUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("{id}")
    @Operation(summary = "Get a user by id",
            description = "Returns a single user, or 404 if no user has that id.")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/search")
    @Operation(summary = "Search users",
            description = "Returns users whose username or email contains the keyword (case-insensitive). "
                    + "An empty keyword returns everyone. Used by the UI to pick a task assignee.")
    public List<User> searchUsers(@RequestParam(required = false, defaultValue = "") String keyword) {
        return userService.searchUsers(keyword);
    }
}