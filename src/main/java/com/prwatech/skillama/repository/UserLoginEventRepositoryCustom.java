package com.prwatech.skillama.repository;

import java.time.LocalDateTime;

public interface UserLoginEventRepositoryCustom {
    /** Count of distinct users with at least one login on/after {@code since}. */
    long countDistinctUsersSince(LocalDateTime since);
}
