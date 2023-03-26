package com.prwatech.job.dto;

import com.prwatech.job.model.JobCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.stereotype.Service;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobDto {
    private String id;

    private String CategoryId;

    private String JobTitle;

    private String JobDescription;

    private String Link;

    private String CompanyName;

    private String Experience;

    private String Location;



}
