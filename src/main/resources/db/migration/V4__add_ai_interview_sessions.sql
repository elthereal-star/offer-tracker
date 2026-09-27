CREATE TABLE IF NOT EXISTS ai_interview_sessions (id BIGINT AUTO_INCREMENT PRIMARY KEY, resume_id BIGINT NOT NULL, application_id BIGINT, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS ai_interview_questions (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL, question_no INT NOT NULL, content TEXT NOT NULL, answer TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_ai_interview_questions_session ON ai_interview_questions (session_id);
