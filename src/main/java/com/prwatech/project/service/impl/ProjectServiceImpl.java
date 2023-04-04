package com.prwatech.project.service.impl;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.project.dto.ProjectDto;
import com.prwatech.project.model.MiniProject;
import com.prwatech.project.repository.ProjectRepository;
import com.prwatech.project.service.ProjectService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProjectServiceImpl implements ProjectService {

  private ProjectRepository projectRepository;

  @Override
  public List<ProjectDto> getProjects() {
    return projectRepository.findAll().stream()
        .map(
            miniProject ->
                new ProjectDto(
                    miniProject.getId(),
                    miniProject.getProjectTitle(),
                    null,
                    miniProject.getProjectBanner(),
                    miniProject.getProjectVideo()))
        .collect(Collectors.toList());
  }

  @Override
  public ProjectDto getProjectDescription(String projectId) {
    MiniProject miniProject =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new NotFoundException("Project With this Id is not present"));
    return new ProjectDto(
        miniProject.getId(),
        miniProject.getProjectTitle(),
        miniProject.getProjectDescription(),
        miniProject.getProjectBanner(),
        miniProject.getProjectVideo());
  }
}
