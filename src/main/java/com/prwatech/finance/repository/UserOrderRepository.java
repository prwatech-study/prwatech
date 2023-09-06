package com.prwatech.finance.repository;

import com.prwatech.finance.model.UserOrder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface UserOrderRepository extends MongoRepository<UserOrder, String> {
}
