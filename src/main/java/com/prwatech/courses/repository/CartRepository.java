package com.prwatech.courses.repository;

import com.prwatech.courses.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@EnableMongoRepositories
@Repository
public interface CartRepository extends MongoRepository<Cart, String> {
}
