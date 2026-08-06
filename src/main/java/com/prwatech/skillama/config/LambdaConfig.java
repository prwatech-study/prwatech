package com.prwatech.skillama.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.time.Duration;

/**
 * AWS Lambda client configuration — same credential-provider/region pattern as {@link S3Config}.
 *
 * <p>The synchronous invoke calls made through this client block for as long as the target
 * function runs, up to that function's own configured timeout (currently 90s for
 * clamav-scanner, 60s for the code sandbox). The SDK's default client-side call timeout isn't
 * guaranteed to outlast that, so it's set explicitly here, comfortably above both — otherwise
 * the caller could give up before a function that's still legitimately running returns.
 */
@Configuration
public class LambdaConfig {

    @Value("${aws.s3.region:ap-south-1}")
    private String region;

    @Bean
    public LambdaClient lambdaClient() {
        return LambdaClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofSeconds(110))
                    .apiCallAttemptTimeout(Duration.ofSeconds(110))
                    .build())
            .build();
    }
}
