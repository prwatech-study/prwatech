package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.regex.Pattern;

@Repository
public class SkillamaUserRepositoryImpl implements SkillamaUserRepositoryCustom {
    
    private final MongoTemplate mongoTemplate;
    
    public SkillamaUserRepositoryImpl(@Qualifier("skillamaMongoTemplate") MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }
    
    @Override
    public Page<User> findUsersWithFilters(String search, User.UserRole role, Boolean active, Pageable pageable) {
        Query query = new Query();
        
        // Search filter (name or email)
        if (search != null && !search.trim().isEmpty()) {
            Pattern pattern = Pattern.compile(search, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                Criteria.where("name").regex(pattern),
                Criteria.where("email").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }
        
        // Role filter
        if (role != null) {
            query.addCriteria(Criteria.where("role").is(role));
        }
        
        // Active filter
        if (active != null) {
            query.addCriteria(Criteria.where("active").is(active));
        }
        
        // Apply pagination and sorting
        query.with(pageable);
        
        // Get total count
        long total = mongoTemplate.count(query, User.class);
        
        // Get results
        List<User> users = mongoTemplate.find(query, User.class);
        
        return new PageImpl<>(users, pageable, total);
    }
}

