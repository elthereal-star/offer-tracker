ALTER TABLE companies ADD owner_id BIGINT NULL;
ALTER TABLE job_applications ADD owner_id BIGINT NULL;
ALTER TABLE resumes ADD owner_id BIGINT NULL;

ALTER TABLE companies ADD CONSTRAINT fk_companies_owner FOREIGN KEY (owner_id) REFERENCES users (id);
ALTER TABLE job_applications ADD CONSTRAINT fk_job_applications_owner FOREIGN KEY (owner_id) REFERENCES users (id);
ALTER TABLE resumes ADD CONSTRAINT fk_resumes_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_companies_owner ON companies (owner_id);
CREATE INDEX idx_job_applications_owner ON job_applications (owner_id);
CREATE INDEX idx_resumes_owner ON resumes (owner_id);
