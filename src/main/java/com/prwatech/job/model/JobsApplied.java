package com.prwatech.job.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.persistence.Id;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "JobsApplied")
public class JobsApplied {

    @Id
    private String id;

    @Field(name = "User_Id")
    private ObjectId User_Id;

    @Field(name = "Job_Id")
    private ObjectId Job_Id;

    @Field(name = "Resume")
    private String Resume;

    @Field(name = "Created_On")
    private LocalDateTime Created_On;
}
