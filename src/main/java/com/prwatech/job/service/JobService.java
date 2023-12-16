package com.prwatech.job.service;

import com.prwatech.job.dto.JobDto;
import java.util.List;

public interface JobService {

  List<JobDto> getJobListing();

  JobDto getJobDescription(String jobId);

  Boolean applyToJob(String userId, String jobId);
}
