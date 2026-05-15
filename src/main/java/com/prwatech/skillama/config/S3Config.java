package com.prwatech.skillama.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS S3 Configuration
 * 
 * Uses default credential provider chain which will:
 * 1. Check environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * 2. Check Java system properties
 * 3. Check EC2 instance profile credentials (IAM role) - for EC2 instances
 * 4. Check ECS container credentials - for ECS tasks
 * 5. Check other credential sources
 * 
 * For EC2 instances with IAM roles, no explicit credentials are needed.
 */
@Configuration
public class S3Config {
    
    @Value("${aws.s3.region:ap-south-1}")
    private String region;
    
    @Bean
    public S3Client s3Client() {
        // Use default credential provider chain
        // This will automatically use IAM role credentials on EC2 instances
        // For local development, you can set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
        // environment variables, or use AWS credentials file
        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}

