package com.prwatech.courses.repository;

import com.prwatech.courses.model.Pricing;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface PricingRepository extends MongoRepository<Pricing, String> {}
