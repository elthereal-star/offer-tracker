ALTER TABLE ai_tasks
    ADD dispatch_status VARCHAR(20) NOT NULL DEFAULT 'NEW';

CREATE INDEX idx_ai_tasks_dispatch ON ai_tasks (dispatch_status, created_at);
