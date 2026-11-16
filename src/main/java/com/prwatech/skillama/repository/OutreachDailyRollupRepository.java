package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.outreach.OutreachDailyRollup;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OutreachDailyRollupRepository extends MongoRepository<OutreachDailyRollup, String> {
    Optional<OutreachDailyRollup> findByDateAndWave(LocalDate date, int wave);

    List<OutreachDailyRollup> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}