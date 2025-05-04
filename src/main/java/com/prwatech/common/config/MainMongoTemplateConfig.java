package com.prwatech.common.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MainMongoTemplateConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean(name = "mongoTemplate")
    public MongoTemplate mongoTemplate() {
        MongoClient mongoClient = MongoClients.create(mongoUri);
        return new MongoTemplate(mongoClient, "prwatechDB"); // Change to your main DB name if different
    }
}
