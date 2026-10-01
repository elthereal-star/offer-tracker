package com.offertracker.storage;

import java.io.IOException;
import java.io.InputStream;

public interface ResumeFileStorage {
    String store(byte[] content) throws IOException;
    default String storeAt(String locator, byte[] content) throws IOException {
        throw new UnsupportedOperationException("Deterministic storage locators are not supported");
    }
    InputStream load(String locator) throws IOException;
    void delete(String locator) throws IOException;
}
