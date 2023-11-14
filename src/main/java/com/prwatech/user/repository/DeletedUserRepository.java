package com.prwatech.user.repository;

import com.prwatech.user.model.DeletedUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface DeletedUserRepository extends MongoRepository<DeletedUser, String> {
}