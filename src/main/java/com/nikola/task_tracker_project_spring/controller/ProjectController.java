package com.nikola.task_tracker_project_spring.controller;

import com.nikola.task_tracker_project_spring.entity.Project;
import com.nikola.task_tracker_project_spring.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    ProjectService projectService;

    @PostMapping
    public Project addProject(@RequestBody @Valid Project project) {
        return projectService.createProject(project);
    }

    @GetMapping
    public Page<Project> findAll(Pageable pageable) {
        return projectService.findAll(pageable);
    }

    @GetMapping("{id}")
    public Project findById(@PathVariable Long id) {
        return projectService.getProjectById(id);
    }

    @GetMapping("/by-owner/{ownerId}")
    public List<Project> findByOwner(@PathVariable Long ownerId) {
        return projectService.getProjectsByOwner(ownerId);
    }

    // Projects the signed-in user can browse tasks in (owned OR assigned-a-task-in).
    @GetMapping("/accessible")
    public List<Project> findAccessible() {
        return projectService.getAccessibleProjects();
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public void deleteById(@PathVariable Long id) {
        projectService.deleteById(id);
    }

    @RequestMapping(value = "{id}", method = RequestMethod.PUT)
    public Project updateById(@PathVariable Long id, @RequestBody @Valid Project project) {
        return projectService.updateById(id, project);
    }
}