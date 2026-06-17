package com.nikola.task_tracker_project_spring.service;

import com.nikola.task_tracker_project_spring.config.AuthFacade;
import com.nikola.task_tracker_project_spring.entity.Project;
import com.nikola.task_tracker_project_spring.entity.Task;
import com.nikola.task_tracker_project_spring.entity.TaskStatus;
import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.entity.TaskActivity;
import com.nikola.task_tracker_project_spring.exception.TaskNotFoundException;
import com.nikola.task_tracker_project_spring.repository.TaskActivityRepository;
import com.nikola.task_tracker_project_spring.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private UserService userService;

    @Mock
    private AuthFacade authFacade;

    @Mock
    private TaskActivityRecorder activityRecorder;

    @Mock
    private TaskActivityRepository activityRepository;

    @InjectMocks
    private TaskService taskService;

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private Task task(String title) {
        Task t = new Task();
        t.setTitle(title);
        t.setStatus(TaskStatus.TODO);
        return t;
    }

    // A task in a project owned by `ownerId`, assigned to `assigneeId` (null allowed).
    private Task task(String title, Long ownerId, Long assigneeId) {
        Task t = task(title);
        Project p = new Project();
        p.setOwner(user(ownerId));
        t.setProject(p);
        if (assigneeId != null) {
            t.setAssignee(user(assigneeId));
        }
        return t;
    }

    /* ----------------------------- Admin: full access ----------------------------- */

    @Test
    void shouldReturnTask_whenIdExistsAndAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        Task t = task("A");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(t));

        assertThat(taskService.getTaskById(1L)).isSameAs(t);
    }

    @Test
    void shouldThrowNotFound_whenTaskDoesNotExist() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(99L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void shouldAttachProjectAndSave_whenAddingTask() {
        Project project = new Project();
        Task t = task("New");
        when(projectService.getProjectById(1L)).thenReturn(project);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task saved = taskService.addTask(1L, t);

        assertThat(saved.getProject()).isSameAs(project);
        verify(taskRepository, times(1)).save(t);
    }

    // Filter correctness lives in TaskSpecificationsTest (@DataJpaTest, real H2). Here we only
    // assert the service contract: the project's existence is checked, then the query is
    // delegated to the Specification-based findAll. Mockito can't introspect a Specification.
    @Test
    void shouldCheckProjectExistsAndScopeToUser_whenNotAdmin() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Task> page = new PageImpl<>(List.of(task("A")));
        when(authFacade.isAdmin()).thenReturn(false);
        when(taskRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<Task> result = taskService.getAllTasks(
                1L, null, null, TaskStatus.TODO, null, null, null, null, pageable);

        verify(projectService).assertExists(1L);
        verify(authFacade).currentUserId();   // visibleTo clamp applied for non-admins
        verify(taskRepository).findAll(any(Specification.class), eq(pageable));
        assertThat(result).isSameAs(page);
    }

    @Test
    void shouldNotScopeToUser_whenAdmin() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Task> page = new PageImpl<>(List.of(task("A")));
        when(authFacade.isAdmin()).thenReturn(true);
        when(taskRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        taskService.getAllTasks(1L, null, null, null, null, null, null, null, pageable);

        verify(projectService).assertExists(1L);
        verify(authFacade, never()).currentUserId();   // admins are not clamped by visibleTo
        verify(taskRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void shouldUpdateMutableFields_whenTaskExistsAndAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        Task existing = task("Old");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task changes = task("Updated");
        changes.setStatus(TaskStatus.COMPLETED);
        changes.setDescription("d");
        Task result = taskService.updateById(1L, changes);

        assertThat(result.getTitle()).isEqualTo("Updated");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(taskRepository).save(existing);
    }

    @Test
    void shouldThrowNotFound_whenUpdatingMissingTask() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.updateById(99L, task("X")))
                .isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void shouldDeleteTask_whenItExistsAndAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task("A")));

        taskService.deleteById(1L);

        verify(taskRepository).deleteById(1L);
    }

    @Test
    void shouldThrowNotFound_whenDeletingMissingTask() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteById(99L))
                .isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).deleteById(anyLong());
    }

    @Test
    void shouldQueryTasksDueToday_whenAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        when(taskRepository.findByDueDate(any(LocalDate.class))).thenReturn(List.of(task("A")));

        assertThat(taskService.getTasksDueToday()).hasSize(1);
        verify(taskRepository).findByDueDate(LocalDate.now());
    }

    @Test
    void shouldResolveUserThenQueryByAssignee_whenAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        when(userService.getUserById(7L)).thenReturn(user(7L));
        when(taskRepository.findByAssigneeId(7L)).thenReturn(List.of(task("A")));

        assertThat(taskService.getTasksByUser(7L)).hasSize(1);
        verify(userService).getUserById(7L);
        verify(taskRepository).findByAssigneeId(7L);
    }

    @Test
    void shouldQueryOverdueTasks_withTodaysDate_whenAdmin() {
        when(authFacade.isAdmin()).thenReturn(true);
        when(taskRepository.findOverdueTasks(any(LocalDate.class))).thenReturn(List.of(task("A")));

        assertThat(taskService.getOverdueTasks()).hasSize(1);
        verify(taskRepository).findOverdueTasks(eq(LocalDate.now()));
    }

    /* ----------------------------- Non-admin: scoped ("both" rule) ---------------------------- */

    @Test
    void shouldReturnTask_whenNotAdminButAssignee() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(5L);
        // Project owned by someone else (2L), but assigned to the current user (5L).
        Task t = task("Assigned to me", 2L, 5L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(t));

        assertThat(taskService.getTaskById(1L)).isSameAs(t);
    }

    @Test
    void shouldDenyTask_whenNotAdminAndNeitherOwnerNorAssignee() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(5L);
        Task t = task("Someone else's", 2L, 3L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> taskService.getTaskById(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldQueryTasksDueToday_scopedToUser_whenNotAdmin() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(5L);
        when(taskRepository.findByDueDateForUser(any(LocalDate.class), eq(5L)))
                .thenReturn(List.of(task("A")));

        assertThat(taskService.getTasksDueToday()).hasSize(1);
        verify(taskRepository).findByDueDateForUser(LocalDate.now(), 5L);
        verify(taskRepository, never()).findByDueDate(any());
    }

    @Test
    void shouldQueryOverdueTasks_scopedToUser_whenNotAdmin() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(5L);
        when(taskRepository.findOverdueTasksForUser(any(LocalDate.class), eq(5L)))
                .thenReturn(List.of(task("A")));

        assertThat(taskService.getOverdueTasks()).hasSize(1);
        verify(taskRepository).findOverdueTasksForUser(LocalDate.now(), 5L);
        verify(taskRepository, never()).findOverdueTasks(any());
    }

    @Test
    void shouldAllowTasksByUser_whenNotAdminAndQueryingSelf() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(5L);
        when(userService.getUserById(5L)).thenReturn(user(5L));
        when(taskRepository.findByAssigneeId(5L)).thenReturn(List.of(task("A")));

        assertThat(taskService.getTasksByUser(5L)).hasSize(1);
        verify(taskRepository).findByAssigneeId(5L);
    }

    @Test
    void shouldDenyTasksByUser_whenNotAdminAndQueryingOther() {
        when(authFacade.isAdmin()).thenReturn(false);
        when(authFacade.currentUserId()).thenReturn(5L);

        assertThatThrownBy(() -> taskService.getTasksByUser(7L))
                .isInstanceOf(AccessDeniedException.class);
        verify(userService, never()).getUserById(anyLong());
        verify(taskRepository, never()).findByAssigneeId(anyLong());
    }

    /* ----------------------------- Audit-trail wiring ----------------------------- */

    @Test
    void shouldRecordCreate_whenAddingTask() {
        Project project = new Project();
        Task t = task("New");
        when(projectService.getProjectById(1L)).thenReturn(project);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task saved = taskService.addTask(1L, t);

        verify(activityRecorder).recordCreate(saved);
    }

    @Test
    void shouldRecordUpdate_beforeSaving_whenUpdatingTask() {
        when(authFacade.isAdmin()).thenReturn(true);
        Task existing = task("Old");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task changes = task("Updated");
        taskService.updateById(1L, changes);

        // The diff must be captured BEFORE the setters/save overwrite the old values.
        InOrder inOrder = inOrder(activityRecorder, taskRepository);
        inOrder.verify(activityRecorder).recordUpdate(existing, changes);
        inOrder.verify(taskRepository).save(existing);
    }

    @Test
    void shouldRecordDelete_whenDeletingTask() {
        when(authFacade.isAdmin()).thenReturn(true);
        Task existing = task("A");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));

        taskService.deleteById(1L);

        verify(activityRecorder).recordDelete(existing);
        verify(taskRepository).deleteById(1L);
    }

    @Test
    void shouldGateActivityByVisibility_thenReturnPage() {
        when(authFacade.isAdmin()).thenReturn(true);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task("A")));
        Pageable pageable = PageRequest.of(0, 20);
        Page<TaskActivity> page = new PageImpl<>(List.of());
        when(activityRepository.findByTaskId(1L, pageable)).thenReturn(page);

        Page<TaskActivity> result = taskService.getTaskActivity(1L, pageable);

        verify(taskRepository).findById(1L);   // getTaskById gate ran first
        assertThat(result).isSameAs(page);
    }

    @Test
    void shouldThrowNotFound_whenReadingActivityOfMissingTask() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskActivity(99L, PageRequest.of(0, 20)))
                .isInstanceOf(TaskNotFoundException.class);
        verify(activityRepository, never()).findByTaskId(anyLong(), any());
    }
}
