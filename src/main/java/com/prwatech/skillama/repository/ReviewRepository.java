package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReviewRepository extends MongoRepository<Review, String> {
}
