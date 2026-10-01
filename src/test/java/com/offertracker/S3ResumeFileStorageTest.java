package com.offertracker;

import com.offertracker.storage.S3ResumeFileStorage;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class S3ResumeFileStorageTest {
    @Test
    void writesPdfObjectsUnderConfiguredPrefixAndDeletesByLocator() {
        S3Client client = mock(S3Client.class);
        S3ResumeFileStorage storage = new S3ResumeFileStorage(client, "resumes", "/offer-tracker/resumes/");

        String locator = storage.store(new byte[] { 1, 2, 3 });

        assertTrue(locator.startsWith("offer-tracker/resumes/"));
        assertTrue(locator.endsWith(".pdf"));
        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        storage.delete(locator);
        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }
}
