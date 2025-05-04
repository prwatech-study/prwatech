package com.prwatech.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableMongoRepositories(
    basePackages = {
        "com.prwatech.user.repository",
        "com.prwatech.finance.repository",
        "com.prwatech.courses.repository",
        "com.prwatech.coupon.repository",
        "com.prwatech.job.repository",
        "com.prwatech.project.repository",
        "com.prwatech.promotion.repository",
        "com.prwatech.quiz.repository"
    }
    // uses the default mongoTemplate bean
)
@Configuration
public class MainMongoConfig {
}
