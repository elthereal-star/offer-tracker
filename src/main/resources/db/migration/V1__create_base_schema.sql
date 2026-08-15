CREATE TABLE IF NOT EXISTS companies (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    website    VARCHAR(256),
    notes      VARCHAR(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_applications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT NOT NULL,
    position     VARCHAR(128) NOT NULL,
    city         VARCHAR(64),
    salary_range VARCHAR(64),
    status       VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    source       VARCHAR(64),
    job_url      VARCHAR(512),
    applied_at   DATE,
    notes        VARCHAR(1024),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_rounds (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    round_no       INT NOT NULL,
    type           VARCHAR(20) NOT NULL,
    scheduled_at   TIMESTAMP,
    result         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    feedback       VARCHAR(1024),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
