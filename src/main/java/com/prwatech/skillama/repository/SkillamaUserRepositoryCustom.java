package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SkillamaUserRepositoryCustom {
    Page<User> findUsersWithFilters(String search, User.UserRole role, Boolean active, Pageable pageable);
}

