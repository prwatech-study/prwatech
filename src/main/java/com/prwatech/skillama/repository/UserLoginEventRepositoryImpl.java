package com.prwatech.skillama.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;

@Repository
public class UserLoginEventRepositoryImpl implements UserLoginEventRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public UserLoginEventRepositoryImpl(@Qualifier("skillamaMongoTemplate") MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long countDistinctUsersSince(LocalDateTime since) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("loggedInAt").gte(since)),
                Aggregation.group("userId"),
                Aggregation.count().as("distinctUsers")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "user_login_events", Map.class);
        Map<String, Object> result = results.getUniqueMappedResult();
        if (result == null || result.get("distinctUsers") == null) {
            return 0L;
        }
        return ((Number) result.get("distinctUsers")).longValue();
    }
}
