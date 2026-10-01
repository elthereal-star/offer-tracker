CREATE TABLE ai_user_configs (
    user_id BIGINT PRIMARY KEY,
    base_url VARCHAR(512) NOT NULL,
    model VARCHAR(128) NOT NULL,
    api_key_ciphertext TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_user_configs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
