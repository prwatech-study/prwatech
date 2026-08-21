package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.PlatformEfficiencyAssumptions;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlatformEfficiencyAssumptionsRepository extends MongoRepository<PlatformEfficiencyAssumptions, String> {
}
