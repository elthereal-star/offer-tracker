package com.offertracker;

import com.offertracker.storage.S3ResumeFileStorage;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class S3ResumeFileStorageMinioIntegrationTest {
    private static final String BUCKET = "offer-tracker-test";
    private static final String ACCESS_KEY = "offer-test-access";
    private static final String SECRET_KEY = "offer-test-secret-key";
    private static final String PREFIX = "offer-tracker/resumes";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2024-12-18T13-15-44Z"))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @Test
    void storesLoadsAndDeletesObjectsUsingS3CompatibleApi() throws Exception {
        try (S3Client client = client()) {
            client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
            S3ResumeFileStorage storage = new S3ResumeFileStorage(client, BUCKET, PREFIX);
            byte[] content = "%PDF-1.7 test resume".getBytes(java.nio.charset.StandardCharsets.UTF_8);

            String generatedLocator = storage.store(content);
            assertTrue(generatedLocator.startsWith(PREFIX + "/"));
            assertTrue(generatedLocator.endsWith(".pdf"));
            try (var downloaded = storage.load(generatedLocator)) {
                assertArrayEquals(content, downloaded.readAllBytes());
            }

            String migrationLocator = storage.storeAt("legacy/42.pdf", content);
            assertEquals(PREFIX + "/legacy/42.pdf", migrationLocator);
            try (var downloaded = storage.load(migrationLocator)) {
                assertArrayEquals(content, downloaded.readAllBytes());
            }

            storage.delete(generatedLocator);
            assertThrows(S3Exception.class, () -> client.headObject(HeadObjectRequest.builder()
                    .bucket(BUCKET).key(generatedLocator).build()));
        }
    }

    private S3Client client() {
        return S3Client.builder()
                .endpointOverride(URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
