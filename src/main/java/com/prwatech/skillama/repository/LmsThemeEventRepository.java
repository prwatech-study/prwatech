package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.LmsThemeEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LmsThemeEventRepository extends MongoRepository<LmsThemeEvent, String> {
    long countByTheme(String theme);
}
