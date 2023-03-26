package com.prwatech.job.service.impl;

import com.prwatech.job.dto.JobDto;
import com.prwatech.job.repository.JobRepository;
import com.prwatech.job.service.JobService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class JobServiceImpl implements JobService {

  private final JobRepository jobRepository;

  @Override
  public List<JobDto> getJobListing() {
    return jobRepository.findAll().stream()
        .map(
            job -> {
              return new JobDto(
                  job.getId(),
                  job.getCategoryId().toString(),
                  job.getJobTitle(),
                  job.getJobDescription(),
                  job.getLink(),
                  job.getCompanyName(),
                  job.getExperience(),
                  job.getLocation());
            })
        .collect(Collectors.toList());
  }
}
