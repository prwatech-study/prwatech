package com.prwatech.job.service;

import com.prwatech.job.dto.JobDto;
import com.prwatech.job.dto.JobListDto;
import com.prwatech.job.repository.JobRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class JobServiceImpl implements JobService {

    JobRepository jobRepository;

    @Override
    public List<JobDto> getJobListing() {
        return jobRepository.findAll().stream().map(job -> {
            return new JobDto(job.getId(), "", job.getJobTitle(), job.getJobDescription(), job.getLink(),
                    job.getCompanyName(), job.getExperience(), job.getLocation());
        }).collect(Collectors.toList());
    }
}
