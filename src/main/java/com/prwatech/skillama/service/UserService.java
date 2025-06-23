package com.prwatech.skillama.service;

import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    private final SkillamaUserRepository userRepository;

    public UserService(SkillamaUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(User user) {
        user.setActive(false);
        user.setActivationKey(generateActivationKey());
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
    
    private String generateActivationKey() {
        return UUID.randomUUID().toString();
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
    
    public User activateUser(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setActive(true);
            user.setActivationKey(null); // Clear activation key once activated
            return userRepository.save(user);
        }
        return null;
    }
    
    public User deactivateUser(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setActive(false);
            user.setActivationKey(generateActivationKey()); // Generate new activation key
            return userRepository.save(user);
        }
        return null;
    }
}
