package com.nikola.task_tracker_project_spring.service;

import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.exception.UserNotFoundException;
import com.nikola.task_tracker_project_spring.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public List<User> searchUsers(String keyword) {
        return userRepository.searchByUsernameOrEmail(keyword == null ? "" : keyword.trim());
    }
}
