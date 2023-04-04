package com.prwatech.project.service.impl;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.project.dto.ProjectDto;
import com.prwatech.project.model.Project;
import com.prwatech.project.repository.ProjectRepository;
import com.prwatech.project.service.ProjectService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private ProjectRepository projectRepository;

    @Override
    public List<ProjectDto> getProjects() {
        return projectRepository
                .findAll()
                .stream()
                .map(project -> new ProjectDto(project.getId(), project.getProjectTitle(), null, project.getProjectBanner(), project.getProjectVideo()))
                .collect(Collectors.toList());
    }

    @Override
    public ProjectDto getProjectDescription(String projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new NotFoundException("Project With this Id is not present"));
        return new ProjectDto(project.getId(), project.getProjectTitle(), project.getProjectDescription(), project.getProjectBanner(), project.getProjectVideo());
    }
}
