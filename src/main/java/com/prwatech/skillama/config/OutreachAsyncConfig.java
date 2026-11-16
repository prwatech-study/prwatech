package com.prwatech.skillama.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class OutreachAsyncConfig {

    public static final String OUTREACH_BATCH_EXECUTOR = "outreachBatchExecutor";

    @Bean(name = OUTREACH_BATCH_EXECUTOR)
    public Executor outreachBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("outreach-batch-");
        executor.initialize();
        return executor;
    }
}