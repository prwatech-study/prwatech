package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.PlatformThemeSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlatformThemeSettingsRepository
        extends MongoRepository<PlatformThemeSettings, String> {
}
