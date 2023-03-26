package com.prwatech.user.repository;

import com.prwatech.user.model.UserOtpMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface UserOtpMappingRepository extends MongoRepository<UserOtpMapping, String> {}
