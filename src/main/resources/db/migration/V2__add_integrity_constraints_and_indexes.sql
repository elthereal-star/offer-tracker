ALTER TABLE job_applications
    ADD CONSTRAINT fk_job_applications_company
    FOREIGN KEY (company_id) REFERENCES companies (id);

ALTER TABLE interview_rounds
    ADD CONSTRAINT fk_interview_rounds_application
    FOREIGN KEY (application_id) REFERENCES job_applications (id)
    ON DELETE CASCADE;

ALTER TABLE interview_rounds
    ADD CONSTRAINT uk_interview_rounds_application_round
    UNIQUE (application_id, round_no);

CREATE INDEX idx_job_applications_company_id
    ON job_applications (company_id);

CREATE INDEX idx_job_applications_status
    ON job_applications (status);

CREATE INDEX idx_job_applications_updated_at
    ON job_applications (updated_at);

CREATE INDEX idx_interview_rounds_application_id
    ON interview_rounds (application_id);
