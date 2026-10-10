CREATE TABLE IF NOT EXISTS ai_interview_request_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_interview_request UNIQUE (owner_id, session_id, question_id, operation, request_key)
);

CREATE INDEX idx_ai_interview_request_created ON ai_interview_request_idempotency (created_at);
