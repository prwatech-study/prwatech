package com.prwatech.skillama.service;

import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {
    private final SkillamaUserRepository userRepository;
    private final MongoTemplate skillamaMongoTemplate;

    public UserService(SkillamaUserRepository userRepository, @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate) {
        this.userRepository = userRepository;
        this.skillamaMongoTemplate = skillamaMongoTemplate;
    }

    public User register(User user) {
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Page<User> findAll(int page, int size, String sortBy, boolean desc) {
        Pageable pageable = PageRequest.of(page, size, desc ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return userRepository.findAll(pageable);
    }
}
