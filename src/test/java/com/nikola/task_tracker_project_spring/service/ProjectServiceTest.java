package com.nikola.task_tracker_project_spring.service;

import com.nikola.task_tracker_project_spring.config.AuthFacade;
import com.nikola.task_tracker_project_spring.entity.Project;
import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.exception.ProjectNotFoundException;
import com.nikola.task_tracker_project_spring.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuthFacade authFacade;

    @InjectMocks
    private ProjectService projectService;

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private Project project(String name) {
        Project p = new Project();
        p.setName(name);
        p.setDescription("desc");
        return p;
    }

    private Project ownedProject(String name, Long ownerId) {
        Project p = project(name);
        p.setOwner(user(ownerId));
        return p;
    }

    /* ----------------------------- Admin: full access ----------------------------- */

    @Test
    void shouldReturnAllProjects_whenAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 10);
        when(projectRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(project("A"), project("B"))));

        assertThat(projectService.findAll(pageable).getContent()).hasSize(2);
        verify(projectRepository).findAll(pageable);
    }

    @Test
    void shouldReturnProject_whenIdExistsAndAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        Project p = ownedProject("A", 5L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThat(projectService.getProjectById(1L)).isSameAs(p);
    }

    @Test
    void shouldThrowNotFound_whenProjectIdMissing() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(99L))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void shouldDelegateGetProjectsByOwner_whenAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        when(projectRepository.findProjectsByOwnerId(5L)).thenReturn(List.of(project("A")));

        assertThat(projectService.getProjectsByOwner(5L)).hasSize(1);
        verify(projectRepository).findProjectsByOwnerId(5L);
    }

    @Test
    void shouldDeleteProject_whenItExistsAndAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(ownedProject("A", 5L)));

        projectService.deleteById(1L);

        verify(projectRepository).deleteById(1L);
    }

    @Test
    void shouldNotDelete_whenProjectMissing() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.deleteById(99L))
                .isInstanceOf(ProjectNotFoundException.class);
        verify(projectRepository, never()).deleteById(anyLong());
    }

    @Test
    void shouldUpdateNameAndDescription_whenProjectExistsAndAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        Project existing = ownedProject("Old", 5L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project changes = project("Updated");
        changes.setDescription("new desc");
        Project result = projectService.updateById(1L, changes);

        assertThat(result.getName()).isEqualTo("Updated");
        assertThat(result.getDescription()).isEqualTo("new desc");
        verify(projectRepository).save(existing);
    }

    @Test
    void shouldThrowNotFound_whenUpdatingMissingProject() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateById(99L, project("X")))
                .isInstanceOf(ProjectNotFoundException.class);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldCreateProjectWithGivenOwner_whenAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        Project input = project("New");
        input.setOwner(user(7L));
        User resolvedOwner = user(7L);
        when(userService.getUserById(7L)).thenReturn(resolvedOwner);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project saved = projectService.createProject(input);

        assertThat(saved.getOwner()).isSameAs(resolvedOwner);
        verify(projectRepository, times(1)).save(input);
    }

    @Test
    void shouldRejectCreate_whenAdminGivesNoOwner() {
        when(authFacade.isAdmin()).thenReturn(true);

        assertThatThrownBy(() -> projectService.createProject(project("New")))
                .isInstanceOf(ResponseStatusException.class);
        verify(projectRepository, never()).save(any());
    }

    /* ----------------------------- Non-admin: scoped ----------------------------- */

    @Test
    void shouldReturnOnlyOwnProjects_whenNotAdmin() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(1L);
        Pageable pageable = PageRequest.of(0, 10);
        when(projectRepository.findByOwnerId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(ownedProject("Mine", 1L))));

        assertThat(projectService.findAll(pageable).getContent()).hasSize(1);
        verify(projectRepository).findByOwnerId(1L, pageable);
        verify(projectRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldReturnOwnProject_whenNotAdminAndOwner() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(1L);
        Project p = ownedProject("Mine", 1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThat(projectService.getProjectById(1L)).isSameAs(p);
    }

    @Test
    void shouldDenyAccess_whenNotAdminAndNotOwner() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(ownedProject("Theirs", 2L)));

        assertThatThrownBy(() -> projectService.getProjectById(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldDenyGetProjectsByOwner_whenNotAdminAndOtherOwner() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(1L);

        assertThatThrownBy(() -> projectService.getProjectsByOwner(2L))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).findProjectsByOwnerId(anyLong());
    }

    @Test
    void shouldForceOwnerToCurrentUser_whenNotAdminCreates() {
        when(authFacade.isAdmin()).thenReturn(false);
        User me = user(1L);
        when(authFacade.currentUser()).thenReturn(me);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        // Even if a different owner is supplied, the server overrides it.
        Project input = project("Mine");
        input.setOwner(user(2L));
        Project saved = projectService.createProject(input);

        assertThat(saved.getOwner()).isSameAs(me);
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner().getId()).isEqualTo(1L);
    }
}
