package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.UserLoginEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserLoginEventRepository extends MongoRepository<UserLoginEvent, String>, UserLoginEventRepositoryCustom {
    List<UserLoginEvent> findByUserIdOrderByLoggedInAtDesc(String userId, Pageable pageable);
    long countByUserId(String userId);
}
