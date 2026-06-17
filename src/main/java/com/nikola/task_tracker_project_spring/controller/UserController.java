package com.nikola.task_tracker_project_spring.controller;

import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    public User createUser(@RequestBody @Valid User user) {
        return userService.createUser(user);
    }

    @GetMapping
    public List<User> getUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/search")
    public List<User> searchUsers(@RequestParam(required = false, defaultValue = "") String keyword) {
        return userService.searchUsers(keyword);
    }
}