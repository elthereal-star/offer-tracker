package com.offertracker;

import com.offertracker.storage.LocalResumeFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocalResumeFileStorageTest {
    @TempDir Path directory;

    @Test
    void storesLoadsAndDeletesResumeContent() throws Exception {
        LocalResumeFileStorage storage = new LocalResumeFileStorage(directory.toString());
        byte[] expected = new byte[] { 37, 80, 68, 70, 45, 49, 46, 55 };
        String locator = storage.store(expected);

        try (InputStream input = storage.load(locator)) {
            assertArrayEquals(expected, input.readAllBytes());
        }
        storage.delete(locator);
        assertFalse(java.nio.file.Files.exists(Path.of(locator)));
    }
}
