package com.prwatech.skillama.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class CertificationBankAsyncConfig {

    public static final String EXECUTOR_NAME = "certificationBankExecutor";

    @Bean(name = EXECUTOR_NAME)
    public Executor certificationBankExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Global single-flight: only one rebuild runs at a time (matches claim lock).
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("cert-bank-");
        executor.initialize();
        return executor;
    }
}
