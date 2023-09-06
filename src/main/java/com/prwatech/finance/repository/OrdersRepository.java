package com.prwatech.finance.repository;

import com.prwatech.finance.model.Orders;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.Optional;

@Repository
@EnableMongoRepositories
public interface OrdersRepository extends MongoRepository<Orders, String> {

    Optional<Orders> findByOrderId(String orderId);
}
