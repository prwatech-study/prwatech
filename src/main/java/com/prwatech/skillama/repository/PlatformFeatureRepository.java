package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.PlatformFeature;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformFeatureRepository extends MongoRepository<PlatformFeature, String> {
    Optional<PlatformFeature> findByCode(String code);

    List<PlatformFeature> findByActiveTrueOrderBySortOrderAsc();
}
