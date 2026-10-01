package com.offertracker.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "offer-tracker.storage.type", havingValue = "s3")
public class S3ResumeFileStorage implements ResumeFileStorage {
    private final S3Client client;
    private final String bucket;
    private final String prefix;

    public S3ResumeFileStorage(S3Client client,
                               @Value("${offer-tracker.storage.s3.bucket}") String bucket,
                               @Value("${offer-tracker.storage.s3.prefix:resumes}") String prefix) {
        this.client = client;
        this.bucket = bucket;
        this.prefix = prefix.replaceAll("^/+|/+$", "");
    }

    @Override
    public String store(byte[] content) {
        String key = prefix + "/" + UUID.randomUUID() + ".pdf";
        put(key, content);
        return key;
    }

    @Override
    public String storeAt(String locator, byte[] content) {
        String relativeKey = locator.replace('\\', '/').replaceAll("^/+", "");
        if (relativeKey.isBlank() || relativeKey.contains("//") ||
                java.util.Arrays.stream(relativeKey.split("/")).anyMatch(part -> part.equals(".") || part.equals(".."))) {
            throw new IllegalArgumentException("Invalid object locator");
        }
        String key = prefix + "/" + relativeKey;
        put(key, content);
        return key;
    }

    private void put(String key, byte[] content) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/pdf").build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public InputStream load(String locator) {
        ResponseInputStream<GetObjectResponse> stream = client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(locator).build());
        return stream;
    }

    @Override
    public void delete(String locator) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(locator).build());
    }
}
