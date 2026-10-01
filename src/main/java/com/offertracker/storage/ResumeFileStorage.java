package com.offertracker.storage;

import java.io.IOException;
import java.io.InputStream;

public interface ResumeFileStorage {
    String store(byte[] content) throws IOException;
    InputStream load(String locator) throws IOException;
    void delete(String locator) throws IOException;
}
