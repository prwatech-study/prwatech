package com.prwatech.user.repository;

import com.prwatech.user.model.Users;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@EnableMongoRepositories
public interface IamRepository extends MongoRepository<Users, String> {


      Optional<Users> findByEmail(String Email);

}
