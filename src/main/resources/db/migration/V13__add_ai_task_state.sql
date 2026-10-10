CREATE TABLE ai_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(128) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP NULL,
    result TEXT NULL,
    error_message VARCHAR(1024) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_tasks_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT uk_ai_tasks_owner_idempotency UNIQUE (owner_id, idempotency_key)
);

CREATE INDEX idx_ai_tasks_status_available ON ai_tasks (status, available_at);
CREATE INDEX idx_ai_tasks_owner_updated ON ai_tasks (owner_id, updated_at);
