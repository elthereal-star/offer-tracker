CREATE TABLE IF NOT EXISTS ai_interview_runtime_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    state_json TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_runtime_snapshot_session UNIQUE (session_id)
);

CREATE INDEX idx_ai_runtime_snapshot_updated ON ai_interview_runtime_snapshots (updated_at);
