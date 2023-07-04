package com.prwatech.finance.repository.template;

import com.prwatech.finance.model.Orders;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class OrderTemplate {

    private final MongoTemplate mongoTemplate;

    public Orders getOrderByOrderId(String orderId)
    {
        Query query = new Query();
        query.addCriteria(Criteria.where("order_id").is(orderId));
        return mongoTemplate.findOne(query, Orders.class);
    }
}
