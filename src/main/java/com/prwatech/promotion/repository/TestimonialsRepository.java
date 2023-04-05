package com.prwatech.promotion.repository;

import com.prwatech.promotion.model.Testimonials;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface TestimonialsRepository extends MongoRepository<Testimonials, String> {}
