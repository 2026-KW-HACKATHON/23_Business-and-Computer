package com.gakkum.backend.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public S3Client s3Client(@Value("${s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${s3.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
}
