package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.PlatformNotificationSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlatformNotificationSettingsRepository
        extends MongoRepository<PlatformNotificationSettings, String> {
}
