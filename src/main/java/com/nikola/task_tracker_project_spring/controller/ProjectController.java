package com.nikola.task_tracker_project_spring.controller;

import com.nikola.task_tracker_project_spring.entity.Project;
import com.nikola.task_tracker_project_spring.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Manage projects and discover which ones the caller can access.")
public class ProjectController {

    @Autowired
    ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a project",
            description = "Creates a new project. Ownership is forced to the authenticated caller, "
                    + "regardless of any owner supplied in the body.")
    public Project addProject(@RequestBody @Valid Project project) {
        return projectService.createProject(project);
    }

    @GetMapping
    @Operation(summary = "List projects",
            description = "Returns a paged list of projects. Regular users see only the projects "
                    + "they own; admins see all projects.")
    public Page<Project> findAll(Pageable pageable) {
        return projectService.findAll(pageable);
    }

    @GetMapping("{id}")
    @Operation(summary = "Get a project by id",
            description = "Returns a single project. Restricted to its owner (admins may read any); "
                    + "a non-owner receives 403 and a missing id returns 404.")
    public Project findById(@PathVariable Long id) {
        return projectService.getProjectById(id);
    }

    @GetMapping("/by-owner/{ownerId}")
    @Operation(summary = "List projects by owner",
            description = "Returns every project owned by the given user.")
    public List<Project> findByOwner(@PathVariable Long ownerId) {
        return projectService.getProjectsByOwner(ownerId);
    }

    @GetMapping("/accessible")
    @Operation(summary = "List projects the caller can browse",
            description = "Returns the projects the signed-in user may browse tasks in — those they "
                    + "own plus any where they are assigned a task. Used to populate the task-view "
                    + "project selector so assignees can reach projects they do not own.")
    public List<Project> findAccessible() {
        return projectService.getAccessibleProjects();
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    @Operation(summary = "Delete a project",
            description = "Deletes a project and, by cascade, all of its tasks. Restricted to the owner.")
    public void deleteById(@PathVariable Long id) {
        projectService.deleteById(id);
    }

    @RequestMapping(value = "{id}", method = RequestMethod.PUT)
    @Operation(summary = "Update a project",
            description = "Updates a project's name and description. Restricted to the owner.")
    public Project updateById(@PathVariable Long id, @RequestBody @Valid Project project) {
        return projectService.updateById(id, project);
    }
}