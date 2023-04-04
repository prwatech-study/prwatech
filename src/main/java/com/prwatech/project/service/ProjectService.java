package com.prwatech.project.service;

import com.prwatech.project.dto.ProjectDto;
import java.util.List;

public interface ProjectService {

  List<ProjectDto> getProjects();

  ProjectDto getProjectDescription(String projectId);
}
