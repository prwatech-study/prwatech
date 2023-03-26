package com.prwatech.job.service;

import com.prwatech.job.dto.JobDto;
import com.prwatech.job.dto.JobListDto;

import java.util.List;

public interface JobService {

    List<JobDto> getJobListing();


}
