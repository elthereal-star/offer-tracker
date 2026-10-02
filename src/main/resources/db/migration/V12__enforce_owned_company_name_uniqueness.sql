ALTER TABLE companies
    ADD normalized_name VARCHAR(128)
        GENERATED ALWAYS AS (LOWER(TRIM(name)));

CREATE UNIQUE INDEX uk_companies_owner_normalized_name
    ON companies (owner_id, normalized_name);
