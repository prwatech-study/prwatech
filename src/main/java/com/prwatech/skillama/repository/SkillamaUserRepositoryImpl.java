package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class SkillamaUserRepositoryImpl implements SkillamaUserRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public SkillamaUserRepositoryImpl(@Qualifier("skillamaMongoTemplate") MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<User> findUsersWithFilters(
            String search,
            User.UserRole role,
            Boolean active,
            String phone,
            User.PlanTier planTier,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {
        Query query = new Query();

        if (search != null && !search.trim().isEmpty()) {
            Pattern pattern = Pattern.compile(search.trim(), Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern),
                    Criteria.where("email").regex(pattern),
                    Criteria.where("phone").regex(pattern)
            ));
        }

        if (role != null) {
            // Legacy accounts may have no role field; treat missing/null as USER (learner).
            if (role == User.UserRole.USER) {
                query.addCriteria(new Criteria().orOperator(
                        Criteria.where("role").is(User.UserRole.USER),
                        Criteria.where("role").is(null),
                        Criteria.where("role").exists(false)));
            } else {
                query.addCriteria(Criteria.where("role").is(role));
            }
        }

        if (active != null) {
            query.addCriteria(Criteria.where("active").is(active));
        }

        if (phone != null && !phone.trim().isEmpty()) {
            Pattern phonePattern = Pattern.compile(phone.trim(), Pattern.CASE_INSENSITIVE);
            query.addCriteria(Criteria.where("phone").regex(phonePattern));
        }

        if (planTier != null) {
            query.addCriteria(Criteria.where("planTier").is(planTier));
        }

        if (fromDate != null || toDate != null) {
            Criteria dateCriteria = Criteria.where("createdAt");
            if (fromDate != null) {
                dateCriteria = dateCriteria.gte(fromDate);
            }
            if (toDate != null) {
                dateCriteria = dateCriteria.lte(toDate);
            }
            query.addCriteria(dateCriteria);
        }

        long total = mongoTemplate.count(query, User.class);
        query.with(pageable);
        List<User> users = mongoTemplate.find(query, User.class);

        return new PageImpl<>(users, pageable, total);
    }
}
