package com.offertracker.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "offer-tracker.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalResumeFileStorage implements ResumeFileStorage {
    private final Path directory;

    public LocalResumeFileStorage(@Value("${offer-tracker.storage.resume-dir:./data/resumes}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(UUID.randomUUID() + ".pdf");
        Files.write(file, content);
        return file.toString();
    }

    @Override
    public InputStream load(String locator) throws IOException {
        return Files.newInputStream(Path.of(locator));
    }

    @Override
    public void delete(String locator) throws IOException {
        Files.deleteIfExists(Path.of(locator));
    }
}
