package com.prwatech.finance.repository.template;

import com.prwatech.finance.model.Wallet;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class WalletTemplate {

  private final MongoTemplate mongoTemplate;

  public Wallet getByUserId(ObjectId id) {
    Query query = new Query(Criteria.where("User_Id").is(id));
    return mongoTemplate.findOne(query, Wallet.class);
  }
}
