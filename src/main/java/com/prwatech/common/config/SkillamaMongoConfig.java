package com.prwatech.common.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(
    basePackages = {
        "com.prwatech.skillama.repository"
    },
    mongoTemplateRef = "skillamaMongoTemplate"
)
public class SkillamaMongoConfig {

    @Value("${skillama.mongodb.uri}")
    private String mongoUri;

    @Bean(name = "skillamaMongoTemplate")
    public MongoTemplate skillamaMongoTemplate() {
        MongoClient mongoClient = MongoClients.create(mongoUri);
        return new MongoTemplate(mongoClient, "skillamaDB");
    }
}
