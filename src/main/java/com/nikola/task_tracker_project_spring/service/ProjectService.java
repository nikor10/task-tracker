package com.nikola.task_tracker_project_spring.service;

import com.nikola.task_tracker_project_spring.config.AuthFacade;
import com.nikola.task_tracker_project_spring.entity.Project;
import com.nikola.task_tracker_project_spring.entity.User;
import com.nikola.task_tracker_project_spring.exception.ProjectNotFoundException;
import com.nikola.task_tracker_project_spring.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectService {
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private AuthFacade authFacade;

    public Page<Project> findAll(Pageable pageable)
    {
        if (authFacade.isAdmin()) {
            return projectRepository.findAll(pageable);
        }
        return projectRepository.findByOwnerId(authFacade.currentUserId(), pageable);
    }

    // Projects the signed-in user can browse tasks in (owned OR assigned-a-task-in).
    // Admins can browse every project. Used to populate the Tasks-tab project dropdown so an
    // assignee can reach their tasks in a project they don't own.
    public List<Project> getAccessibleProjects()
    {
        if (authFacade.isAdmin()) {
            return projectRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        }
        return projectRepository.findAccessibleProjects(authFacade.currentUserId());
    }

    public Project createProject(Project project)
    {
        if (authFacade.isAdmin()) {
            // Admin must specify an existing owner for the new project.
            if (project.getOwner() == null || project.getOwner().getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner is required");
            }
            project.setOwner(userService.getUserById(project.getOwner().getId()));
        } else {
            // Regular users always own the projects they create.
            project.setOwner(authFacade.currentUser());
        }
        return projectRepository.save(project);
    }

    public Project getProjectById(Long id)
    {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
        assertVisible(project);
        return project;
    }

    // Existence-only check (404 if missing) without the owner-only visibility gate.
    // Used where row-level authorization happens on the results instead (e.g. task search).
    public void assertExists(Long id)
    {
        if (!projectRepository.existsById(id)) {
            throw new ProjectNotFoundException(id);
        }
    }

    public List<Project> getProjectsByOwner(Long ownerId)
    {
        if (!authFacade.isAdmin() && !ownerId.equals(authFacade.currentUserId())) {
            throw new AccessDeniedException("You can only view your own projects");
        }
        return projectRepository.findProjectsByOwnerId(ownerId);
    }

    public void deleteById(Long id)
    {
        getProjectById(id); // enforces ownership/visibility
        projectRepository.deleteById(id);
    }

    public Project updateById(Long id, Project project)
    {
        Project existingProject = getProjectById(id); // enforces ownership/visibility
        existingProject.setName(project.getName());
        existingProject.setDescription(project.getDescription());
        return projectRepository.save(existingProject);
    }

    // A non-admin user may only touch projects they own.
    private void assertVisible(Project project)
    {
        if (authFacade.isAdmin()) {
            return;
        }
        User owner = project.getOwner();
        if (owner == null || !owner.getId().equals(authFacade.currentUserId())) {
            throw new AccessDeniedException("You can only access your own projects");
        }
    }
}
