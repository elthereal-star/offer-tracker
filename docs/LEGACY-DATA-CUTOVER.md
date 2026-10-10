# Legacy Data Ownership Cutover

Pre-identity records have nullable `owner_id` columns so desktop data and history remain intact. Once authenticated access is enabled, those unowned rows are intentionally hidden. Do not assign them automatically during registration: that would let the first registrant claim another deployment's existing data.

Before opening an upgraded installation to users:

1. Take and verify a database backup, and stop writes to the application.
2. Create or identify the account that is authorized to receive the existing dataset. Verify its phone/account out of band.
3. Review the counts of rows with `owner_id IS NULL` in `companies`, `job_applications`, `resumes`, and `ai_interview_sessions`. Confirm the unowned data belongs to this one account. If it contains multiple people's data, do not run the bulk update; partition and assign records using a reviewed mapping.
4. Run the following transaction with the verified account ID substituted for `@legacy_owner_id`:

```sql
START TRANSACTION;
SET @legacy_owner_id = 123;

SELECT id FROM users WHERE id = @legacy_owner_id FOR UPDATE;
UPDATE companies SET owner_id = @legacy_owner_id WHERE owner_id IS NULL;
UPDATE job_applications SET owner_id = @legacy_owner_id WHERE owner_id IS NULL;
UPDATE resumes SET owner_id = @legacy_owner_id WHERE owner_id IS NULL;
UPDATE ai_interview_sessions SET owner_id = @legacy_owner_id WHERE owner_id IS NULL;

SELECT 'companies' AS table_name, COUNT(*) AS unowned FROM companies WHERE owner_id IS NULL
UNION ALL SELECT 'job_applications', COUNT(*) FROM job_applications WHERE owner_id IS NULL
UNION ALL SELECT 'resumes', COUNT(*) FROM resumes WHERE owner_id IS NULL
UNION ALL SELECT 'ai_interview_sessions', COUNT(*) FROM ai_interview_sessions WHERE owner_id IS NULL;
```

5. Review the resulting counts and application-level data with the intended account before `COMMIT`. Use `ROLLBACK` if any count or ownership assumption is unexpected.
6. Start with `AUTH_REQUIRED=true` and verify the account can see its expected history while a second account cannot.

This procedure is deliberately operator-driven. It is not safe for a dataset that already contains multiple owners or whose provenance is unknown.
