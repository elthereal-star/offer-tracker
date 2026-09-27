CREATE TABLE IF NOT EXISTS resumes (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id    BIGINT,
    original_filename VARCHAR(255) NOT NULL,
    storage_path      VARCHAR(512) NOT NULL,
    content_type      VARCHAR(128) NOT NULL,
    size_bytes        BIGINT NOT NULL,
    extracted_text    TEXT NOT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resumes_application_id ON resumes (application_id);
