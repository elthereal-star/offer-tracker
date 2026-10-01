package com.offertracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "offer-tracker.storage.type", havingValue = "s3")
public class ObjectStorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${offer-tracker.storage.s3.endpoint}") URI endpoint,
                      @Value("${offer-tracker.storage.s3.region}") String region,
                      @Value("${offer-tracker.storage.s3.path-style-access:true}") boolean pathStyleAccess) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build())
                .build();
    }
}
