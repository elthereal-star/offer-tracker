CREATE INDEX idx_ai_tasks_outbox_recovery
    ON ai_tasks (status, dispatch_status, available_at, updated_at);
