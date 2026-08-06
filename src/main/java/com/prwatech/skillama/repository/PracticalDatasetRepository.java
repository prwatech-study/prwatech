package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.PracticalDataset;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PracticalDatasetRepository extends MongoRepository<PracticalDataset, String> {
    Optional<PracticalDataset> findByDatasetId(String datasetId);

    Optional<PracticalDataset> findByCourseIdAndContentHashAndDeletedAtIsNull(String courseId, String contentHash);
}
