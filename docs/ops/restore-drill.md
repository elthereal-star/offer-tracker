# Restore Drill Runbook

This runbook is a rehearsal procedure, not an automatic cleanup job. Perform it in an isolated recovery environment first and keep the original production data untouched.

## Recovery objectives

- Define an owner-approved RPO (maximum acceptable data loss) and RTO (maximum acceptable recovery time) before the drill.
- Record the backup timestamps, Flyway version, application image digest, and object-storage prefix used by the rehearsal.
- Treat MySQL as the system of record. Redis contains shared rate-limit state and AI task delivery records; it may be rebuilt empty because pending task state remains in MySQL and the outbox re-dispatches unclaimed deliveries after `AI_TASK_DISPATCH_RETRY_DELAY` (default one minute). Redis is not an authoritative task ledger.

## Before the drill

1. Confirm a verified MySQL backup and a separate object-storage backup/versioning policy.
2. Export the relevant secret references without writing secret values into this repository or the drill log.
3. Provision an isolated MySQL, Redis, and S3-compatible recovery target. Block public traffic to it.
4. Record the source database Flyway version and the expected application commit.

## Restore and verify

1. Restore the MySQL backup into the isolated database and run the application with `mysql,production` against it.
2. Let Flyway apply only pending migrations; stop if a migration fails and preserve the database for investigation.
3. Restore resume objects to the configured bucket/prefix and verify object counts and checksums against the backup manifest.
4. Start Redis empty and verify that the readiness endpoint becomes healthy after Redis is reachable. Confirm the production worker creates its consumer group and that old `PENDING` MySQL tasks with a previously published delivery are re-enqueued after the configured retry delay.
5. Run authenticated smoke checks: login/logout, company and application ownership, resume download, interview history, and AI configuration isolation.
6. Run the data export endpoint and compare record counts with the pre-drill manifest. Do not compare secrets or raw AI credentials.
7. Verify `/actuator/health/liveness`, `/actuator/health/readiness`, and Prometheus scraping from the management network only.

## Exit and evidence

- Record elapsed restore time, missing objects, failed checks, and the observed RPO/RTO.
- Keep the isolated recovery environment until the evidence is reviewed and signed off.
- Do not delete or overwrite the source database, source object prefix, local legacy files, or historical exports as part of this drill.
- Repeat after schema, storage-provider, or deployment-topology changes.

## AI task queue checks

During the rehearsal, verify that the Redis consumer group `offer-tracker-ai-workers` can consume a test task, an empty Redis stream is rebuilt from an old `PENDING` MySQL task, a worker restart recovers an expired lease, and a task after the retry limit appears in `offer-tracker:ai-tasks:dead-letter`. The MySQL `ai_tasks` row is authoritative; Redis stream entries are delivery state and must not be treated as the only audit record. Confirm provider call counts separately: delivery is at-least-once and a repeated billable request is possible after a crash or lease expiry.
