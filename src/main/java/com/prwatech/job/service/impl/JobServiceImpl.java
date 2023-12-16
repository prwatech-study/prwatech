package com.prwatech.job.service.impl;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.job.dto.JobDto;
import com.prwatech.job.model.Jobs;
import com.prwatech.job.model.JobsApplied;
import com.prwatech.job.repository.JobRepository;
import com.prwatech.job.repository.JobsAppliedRepository;
import com.prwatech.job.service.JobService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class JobServiceImpl implements JobService {

  private final JobRepository jobRepository;
  private final JobsAppliedRepository jobsAppliedRepository;

  @Override
  public List<JobDto> getJobListing() {
    return jobRepository.findAll().stream()
        .map(
            job -> {
              return new JobDto(
                  job.getId(),
                  job.getCategoryId().toString(),
                  job.getJobTitle(),
                  null,
                  job.getLink(),
                  job.getCompanyName(),
                  job.getExperience(),
                  job.getLocation());
            })
        .collect(Collectors.toList());
  }

  @Override
  public JobDto getJobDescription(String jobId) {
    Optional<Jobs> jobs = jobRepository.findById(jobId);
    if (jobs.isEmpty() || !jobs.isPresent()) {
      throw new NotFoundException("No job listing found for this Job!");
    }

    JobDto jobDto = new JobDto();
    jobDto.setId(jobs.get().getId());
    jobDto.setCategoryId(jobs.get().getCategoryId().toString());
    jobDto.setJobTitle(jobs.get().getJobTitle());
    jobDto.setJobDescription(jobs.get().getJobDescription());
    jobDto.setLink(jobs.get().getLink());
    jobDto.setCompanyName(jobs.get().getCompanyName());
    jobDto.setExperience(jobs.get().getExperience());
    jobDto.setLocation(jobs.get().getLocation());

    return jobDto;
  }

  @Override
  public Boolean applyToJob(String userId, String jobId) {

    JobsApplied jobsApplied = JobsApplied.builder()
            .Job_Id(new ObjectId(jobId))
            .User_Id(new ObjectId(userId))
            .Created_On(LocalDateTime.now())
            .build();

    jobsAppliedRepository.save(jobsApplied);
    return Boolean.TRUE;
  }
}
