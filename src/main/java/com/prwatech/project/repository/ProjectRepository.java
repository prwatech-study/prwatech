package com.prwatech.project.repository;

import com.prwatech.project.model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface ProjectRepository extends MongoRepository<Project,String> {

}
