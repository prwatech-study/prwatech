package com.prwatech.project.controller;

import com.prwatech.project.dto.ProjectDto;
import com.prwatech.project.service.ProjectService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/public")
@AllArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping(value = "/projects")
    public List<ProjectDto> getProjectsList() {

        return projectService.getProjects();


    }

    @GetMapping(value = "/project/description")
    public ProjectDto getProjectDescription(@RequestParam(value = "projectId") String projectId){
     return  projectService.getProjectDescription(projectId);
    }



}
