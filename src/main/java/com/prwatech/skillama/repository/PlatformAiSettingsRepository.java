package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.PlatformAiSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlatformAiSettingsRepository
        extends MongoRepository<PlatformAiSettings, String> {
}
