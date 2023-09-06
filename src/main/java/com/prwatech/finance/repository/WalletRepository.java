package com.prwatech.finance.repository;

import com.prwatech.finance.model.Wallet;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@EnableMongoRepositories
@Repository
public interface WalletRepository extends MongoRepository<Wallet, String> {}
