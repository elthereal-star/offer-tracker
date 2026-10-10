ALTER TABLE ai_interview_sessions ADD owner_id BIGINT NULL;
ALTER TABLE ai_interview_sessions ADD CONSTRAINT fk_ai_interview_sessions_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_ai_interview_sessions_owner ON ai_interview_sessions (owner_id);
