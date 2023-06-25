package com.prwatech.courses.repository;

import com.prwatech.courses.model.Cart;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;


@AllArgsConstructor
@Component
public class CartTemplate {

    private final MongoTemplate mongoTemplate;

    public List<Cart> getByUserId(ObjectId User_Id){
        Query query = new Query();
        query.addCriteria(Criteria.where("User_Id").is(User_Id));
        return mongoTemplate.find(query, Cart.class);
    }

    public Optional<Cart> getACartByUserId(ObjectId User_Id){
        Query query = new Query();
        query.addCriteria(Criteria.where("User_Id").is(User_Id));
        return Optional.ofNullable(mongoTemplate.findOne(query, Cart.class));
    }
}
