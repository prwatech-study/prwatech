package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SkillamaUserRepositoryCustom {
    Page<User> findUsersWithFilters(
            String search,
            User.UserRole role,
            Boolean active,
            String phone,
            User.PlanTier planTier,
            java.time.LocalDateTime fromDate,
            java.time.LocalDateTime toDate,
            Pageable pageable);
}

