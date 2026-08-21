package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.TimeWalletAdjustmentEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TimeWalletAdjustmentEventRepository extends MongoRepository<TimeWalletAdjustmentEvent, String> {
    List<TimeWalletAdjustmentEvent> findByUserIdOrderByCreatedAtDesc(String userId);
}
