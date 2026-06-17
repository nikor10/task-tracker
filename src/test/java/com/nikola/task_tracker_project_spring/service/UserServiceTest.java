package com.nikola.task_tracker_project_spring.service;

import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.exception.UserNotFoundException;
import com.nikola.task_tracker_project_spring.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User newUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setPassword("rawPassword");
        return u;
    }

    @Test
    void shouldEncodePasswordAndSave_whenCreatingUser() {
        User input = newUser("alice");
        when(passwordEncoder.encode("rawPassword")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.createUser(input);

        assertThat(saved.getPassword()).isEqualTo("ENCODED");
        verify(passwordEncoder).encode("rawPassword");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("ENCODED");
    }

    @Test
    void shouldReturnAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(newUser("a"), newUser("b")));

        assertThat(userService.getAllUsers()).hasSize(2);
        verify(userRepository).findAll();
    }

    @Test
    void shouldReturnUser_whenIdExists() {
        User u = newUser("alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        assertThat(userService.getUserById(1L)).isSameAs(u);
    }

    @Test
    void shouldThrowNotFound_whenIdDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void shouldDelegateSearchToRepository() {
        when(userRepository.searchByUsernameOrEmail("alice")).thenReturn(List.of(newUser("alice")));

        List<User> result = userService.searchUsers("alice");

        assertThat(result).hasSize(1);
        verify(userRepository).searchByUsernameOrEmail("alice");
    }

    @Test
    void shouldTrimKeyword_andConvertNullToEmpty() {
        when(userRepository.searchByUsernameOrEmail(anyString())).thenReturn(List.of());

        userService.searchUsers("  bob  ");
        userService.searchUsers(null);

        verify(userRepository).searchByUsernameOrEmail("bob");
        verify(userRepository).searchByUsernameOrEmail(eq(""));
    }
}
