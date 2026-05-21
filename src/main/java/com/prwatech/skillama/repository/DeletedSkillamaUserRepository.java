package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.DeletedSkillamaUser;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeletedSkillamaUserRepository extends MongoRepository<DeletedSkillamaUser, String> {
}
