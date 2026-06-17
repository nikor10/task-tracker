package com.nikola.task_tracker_project_spring.service;

import com.nikola.task_tracker_project_spring.config.AuthFacade;
import com.nikola.task_tracker_project_spring.entity.Project;
import com.nikola.task_tracker_project_spring.entity.Task;
import com.nikola.task_tracker_project_spring.entity.TaskPriority;
import com.nikola.task_tracker_project_spring.entity.TaskStatus;
import com.nikola.task_tracker_project_spring.entity.TaskActivity;
import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.event.TaskAssignedEvent;
import com.nikola.task_tracker_project_spring.exception.TaskNotFoundException;
import com.nikola.task_tracker_project_spring.repository.TaskActivityRepository;
import com.nikola.task_tracker_project_spring.repository.TaskRepository;
import com.nikola.task_tracker_project_spring.repository.TaskSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TaskService {
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    ProjectService projectService;
    @Autowired
    UserService userService;
    @Autowired
    AuthFacade authFacade;
    @Autowired
    ApplicationEventPublisher eventPublisher;
    @Autowired
    TaskActivityRecorder activityRecorder;
    @Autowired
    TaskActivityRepository activityRepository;

    public Task getTaskById(Long id)
    {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        assertVisible(task);
        return task;
    }

    // Reuses getTaskById's gate: 404 if the task is missing, 403 if it isn't visible to the
    // caller. So the audit trail is never exposed more widely than the task it describes.
    public Page<TaskActivity> getTaskActivity(Long id, Pageable pageable)
    {
        getTaskById(id);
        return activityRepository.findByTaskId(id, pageable);
    }

    // @Transactional so the event below is published within a transaction; the
    // AFTER_COMMIT listener only fires once that transaction actually commits.
    @Transactional
    public Task addTask(Long id, Task task)
    {
        // getProjectById enforces that the caller owns the project (or is admin).
        Project project = projectService.getProjectById(id);
        task.setProject(project);
        Task saved = taskRepository.save(task);

        // Audit the creation in the same transaction as the save.
        activityRecorder.recordCreate(saved);

        // The request body typically supplies only the assignee's id, so resolve the
        // full user to obtain a valid email before notifying them.
        if (saved.getAssignee() != null && saved.getAssignee().getId() != null) {
            User assignee = userService.getUserById(saved.getAssignee().getId());
            saved.setAssignee(assignee);
            eventPublisher.publishEvent(new TaskAssignedEvent(
                    saved.getId(),
                    saved.getTitle(),
                    project.getName(),
                    assignee.getUsername(),
                    assignee.getEmail()));
        }
        return saved;
    }

    public Page<Task> getAllTasks(Long projectId, String title, String description,
                                  TaskStatus status, TaskPriority priority, Long assigneeId,
                                  LocalDate dueAfter, LocalDate dueBefore, Pageable pageable)
    {
        // Existence only (404 if missing). Access is enforced per-row via visibleTo below,
        // so an assignee can list their tasks in a project they don't own.
        projectService.assertExists(projectId);

        Specification<Task> spec = Specification.where(TaskSpecifications.belongsToProject(projectId));
        if (title != null && !title.isBlank()) {
            spec = spec.and(TaskSpecifications.titleContains(title));
        }
        if (description != null && !description.isBlank()) {
            spec = spec.and(TaskSpecifications.descriptionContains(description));
        }
        if (status != null) {
            spec = spec.and(TaskSpecifications.hasStatus(status));
        }
        if (priority != null) {
            spec = spec.and(TaskSpecifications.hasPriority(priority));
        }
        if (assigneeId != null) {
            spec = spec.and(TaskSpecifications.assignedTo(assigneeId));
        }
        if (dueAfter != null) {
            spec = spec.and(TaskSpecifications.dueAfter(dueAfter));
        }
        if (dueBefore != null) {
            spec = spec.and(TaskSpecifications.dueBefore(dueBefore));
        }

        // Non-admins only ever see tasks they own (via project) or are assigned to.
        if (!authFacade.isAdmin()) {
            spec = spec.and(TaskSpecifications.visibleTo(authFacade.currentUserId()));
        }

        return taskRepository.findAll(spec, pageable);
    }

    // @Transactional so the audit rows and the task update commit (or roll back) as a unit.
    @Transactional
    public Task updateById(Long id, Task task)
    {
        Task existingTask = getTaskById(id); // enforces visibility

        // Diff old-vs-new BEFORE the setters overwrite the old values in memory.
        activityRecorder.recordUpdate(existingTask, task);

        existingTask.setDueDate(task.getDueDate());
        existingTask.setAssignee(task.getAssignee());
        existingTask.setTitle(task.getTitle());
        existingTask.setDescription(task.getDescription());
        existingTask.setPriority(task.getPriority());
        existingTask.setStatus(task.getStatus());
        return taskRepository.save(existingTask);
    }

    // @Transactional so the DELETE audit row and the deletion commit (or roll back) as a unit.
    @Transactional
    public void deleteById(Long id)
    {
        Task task = getTaskById(id); // enforces visibility

        // Record the deletion while the task is still loaded, in the same transaction.
        activityRecorder.recordDelete(task);

        taskRepository.deleteById(id);
    }

    public List<Task> getTasksDueToday()
    {
        LocalDate date = LocalDate.now();
        if (authFacade.isAdmin()) {
            return taskRepository.findByDueDate(date);
        }
        return taskRepository.findByDueDateForUser(date, authFacade.currentUserId());
    }

    public List<Task> getTasksByUser(Long userId)
    {
        if (!authFacade.isAdmin() && !userId.equals(authFacade.currentUserId())) {
            throw new AccessDeniedException("You can only view your own tasks");
        }
        User user = userService.getUserById(userId);
        return taskRepository.findByAssigneeId(user.getId());
    }

    public List<Task> getOverdueTasks()
    {
        if (authFacade.isAdmin()) {
            return taskRepository.findOverdueTasks(LocalDate.now());
        }
        return taskRepository.findOverdueTasksForUser(LocalDate.now(), authFacade.currentUserId());
    }

    // "Both" visibility: a non-admin user may see a task if they own its project
    // OR they are the assignee.
    private void assertVisible(Task task)
    {
        if (authFacade.isAdmin()) {
            return;
        }
        Long userId = authFacade.currentUserId();
        boolean ownsProject = task.getProject() != null
                && task.getProject().getOwner() != null
                && task.getProject().getOwner().getId().equals(userId);
        boolean assignedToUser = task.getAssignee() != null
                && task.getAssignee().getId().equals(userId);
        if (!ownsProject && !assignedToUser) {
            throw new AccessDeniedException("You can only access your own tasks");
        }
    }
}
