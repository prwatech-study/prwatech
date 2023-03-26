package com.prwatech.job.service;

import com.prwatech.job.dto.JobDto;
import java.util.List;

public interface JobService {

  List<JobDto> getJobListing();
}
