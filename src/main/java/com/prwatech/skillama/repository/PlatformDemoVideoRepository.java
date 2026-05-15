package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.PlatformDemoVideo;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlatformDemoVideoRepository extends MongoRepository<PlatformDemoVideo, String> {
}
