package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.UpgradeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UpgradeRequestRepository extends MongoRepository<UpgradeRequest, String> {
    Page<UpgradeRequest> findByStatusOrderByCreatedAtDesc(
            UpgradeRequest.RequestStatus status, Pageable pageable);

    Page<UpgradeRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("{ $or: [ " +
            "{ 'userEmail': { $regex: ?0, $options: 'i' } }, " +
            "{ 'userName': { $regex: ?0, $options: 'i' } } " +
            "] }")
    Page<UpgradeRequest> searchByUser(String search, Pageable pageable);
}
