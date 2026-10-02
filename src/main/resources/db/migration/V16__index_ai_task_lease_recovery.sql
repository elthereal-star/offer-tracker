CREATE INDEX idx_ai_tasks_status_lease
    ON ai_tasks (status, lease_until);
