# Production Baseline

This document records the rules for the productionization work. It is a baseline and checklist, not a claim that all items are already implemented.

## Module Map

| Module | Owns | Current status |
| --- | --- | --- |
| Identity | Users, credentials, sessions, roles | Phone + password registration/login, Sa-Token sessions, production authentication enforcement, Redis-shared login/IP throttling; phone ownership is not verified |
| Company | Company records and company search | Owner-scoped; anonymous compatibility mode remains for local use |
| Application | Job applications and status changes | Owner-scoped; anonymous compatibility mode remains for local use |
| Interview | Manual interview rounds and results | Ownership checked through the parent application |
| Resume | PDF metadata, extracted text, file lifecycle | Owner-scoped metadata; local filesystem and S3-compatible storage adapters |
| AI Interview | Sessions, questions, answers, scoring, reports | Owner-scoped synchronous sessions |
| AI Provider | OpenAI-compatible configuration and client adapter | User-scoped AES-GCM encrypted credentials; anonymous local mode retains file config |
| Analytics | Aggregated application statistics | Owner-scoped when authenticated |
| Data Transfer | JSON backup, restore, and CSV export | Owner-scoped when authenticated |

## Delivery Phase Status

The target is a production-capable modular monolith sized through staged measurements, not an unsubstantiated million-user guarantee. “Implemented” means repository code and automated tests exist; it does not mean a live deployment or provider has been accepted.

| Phase | Scope | Status | Evidence / remaining gate |
| --- | --- | --- | --- |
| 0. Architecture and domain baseline | Modular monolith, canonical terms, explicit constraints | Implemented | ADR 0001, `CONTEXT.md`, and this baseline |
| 1. Identity and ownership | Phone + password, Sa-Token sessions, owner isolation, encrypted per-user AI keys | Core implemented | Phone ownership and password recovery are not verified/available; gate public registration until SMS verification or invitation control is added; per-install legacy ownership cutover remains |
| 2. Multi-instance foundations | MySQL, Redis-shared limits, S3-compatible storage adapter, controlled legacy file migration | Implemented in code; S3 API lifecycle integration-tested against Adobe S3Mock on `9fa7968` ([push CI](https://github.com/elthereal-star/offer-tracker/actions/runs/37012831832), [PR CI](https://github.com/elthereal-star/offer-tracker/actions/runs/37012840089)) | Select and verify actual providers; run object compatibility, backup, and restore drills |
| 3. Operations and security | Health probes, request IDs, metrics, alert templates, proxy trust controls, read-only load baseline | Implemented in code/docs | Configure deployment monitoring and secrets, route alerts, define RPO/RTO, and record staged capacity results |
| 4. Asynchronous AI tasks | MySQL outbox, owner-scoped idempotent API, Redis Streams worker, lease recovery, bounded retries, dead letters | Core implemented | All 76 backend tests passed in GitHub CI on `301935f`, including all 6 Redis-container worker cases ([run](https://github.com/elthereal-star/offer-tracker/actions/runs/37008082886)); actual provider timeout and duplicate-charge behavior still require a provider-backed rehearsal |
| 5. Deployment acceptance | Runbook-based release, recovery rehearsal, provider and capacity sign-off | Not yet accepted | Requires a staging/production account, a registration trust policy, chosen storage/AI providers, credentials, and operator evidence |

No phase claims a million-user capacity target. Establish the supported workload from measured RPS, latency, error rate, database/Redis saturation, and cost in an isolated staging environment before setting a public capacity claim.

## Current Production Gaps

- Start public deployments with `--spring.profiles.active=mysql,production`. The `production` profile requires MySQL and Redis credentials plus the AI configuration encryption key, and fails startup if authentication is disabled by any configuration source.
- Provide `AI_CONFIG_ENCRYPTION_KEY` with at least 32 characters. It must be backed up and kept stable; rotating it requires re-encrypting stored AI credentials. The fallback value in the default profile is local-only and must never be used publicly. Sa-Token session state is shared through Redis.
- Complete the legacy ownership cutover before enabling public access: records created before owner IDs were introduced have `owner_id IS NULL`, and authenticated queries intentionally hide them. The controlled migration runner supports a default dry-run and explicit `--apply` for a verified active account; never auto-claim them for the first registrant.
- Registration currently accepts a phone number as an unverified login identifier. Do not expose open registration publicly until phone ownership verification or an invite-only registration policy and account recovery are implemented.
- AI provider endpoints are user-supplied. In `production`, the application requires HTTPS and rejects URL credentials, query/fragment components, localhost names, and IP-literal hosts. This does not prevent a public hostname from resolving or rebinding to an internal address; enforce outbound network rules that deny loopback, private, link-local, and cloud metadata destinations before exposing BYOK AI calls.
- Use a managed MySQL service. The default profile remains local H2.
- Resume files can use the S3-compatible adapter for multi-instance deployments; configure the provider endpoint, region, and bucket, and provide credentials through the AWS SDK default credential chain. The adapter's bucket creation, object write/read, stable-key migration, and deletion lifecycle is integration-tested against Adobe S3Mock in GitHub CI. This does not establish compatibility with a selected cloud vendor; verify that provider's compatibility, access policy, lifecycle, and backup behavior before go-live.
- Existing resume files have local filesystem locators. Before switching an existing installation to S3, migrate each object and update its database locator during a controlled cutover; do not change `RESUME_STORAGE_TYPE` alone. The migration CLI is implemented, but provider-specific live compatibility testing and a full restore rehearsal remain before go-live.
- Production login attempts are atomically limited to five attempts per phone in a 15-minute window and cleared after successful login. AI generation/scoring calls are limited to 10 per authenticated user per minute by default (`AI_REQUESTS_PER_MINUTE`), shared through Redis across instances. Authentication endpoints also have a shared Redis limit of 60 requests per minute per client IP. Configure `TRUSTED_PROXY_CIDRS` with the reverse-proxy CIDRs; only connections from those CIDRs may supply the client IP through `X-Forwarded-For`, and direct public access to the application port must be blocked. Alert templates, restore rehearsal documentation, and a read-only capacity-test baseline are provided; they still require deployment-specific execution and sign-off.
- A starter Prometheus alert set is provided at `docs/ops/prometheus-alerts.yml`. Import it into the monitoring stack, configure the readiness probe job label, and route critical alerts to on-call. Thresholds are initial baselines, not capacity guarantees; tune them after collecting production traffic and latency distributions.
- A restore rehearsal procedure is provided at `docs/ops/restore-drill.md`. It is intentionally manual and non-destructive: MySQL is authoritative, Redis may be rebuilt empty and pending AI deliveries are replayed from the outbox, resume objects are checksum-verified, and the source environment is retained.
- A read-only k6 capacity baseline is provided at `docs/ops/k6-readonly-smoke.js` with execution notes in `docs/ops/capacity-test.md`. It is not a million-user claim; use staged tests to establish measured limits.
- Redis Streams queue publishing, outbox replay, duplicate delivery, expired-lease redelivery, simulated provider timeouts, bounded retries, and retry-exhaustion dead letters have container-backed tests. All six worker container cases passed on `301935f` in GitHub CI. Outbox selection/recovery has H2 integration coverage and worker claim state has unit coverage. A live deployment rehearsal described in `docs/ops/restore-drill.md` and real provider timeout/duplicate-charge verification remain outstanding.
- SMS verification is deferred because no cloud provider has been selected. Its planned adapter requirements are recorded in `docs/ops/sms-adapter.md`; complete delivery and abuse testing before enabling phone ownership checks.
- ADR 0002 records the accepted first-phase design for asynchronous AI tasks. Task submission is idempotent within an owner, but Redis delivery and provider execution are at-least-once: an expired lease or lost Redis state can cause a provider call to repeat. The AI provider adapter has no provider-neutral idempotency guarantee, so retries may incur duplicate charges. Do not claim exactly-once execution or duplicate-billing prevention.

## Required Production Interfaces

Every externally reachable interface must define:

- Authentication and authorization requirements.
- Resource ownership checks.
- Input limits and validation rules.
- Pagination and ordering behavior.
- Idempotency behavior for retryable writes.
- Error codes safe for clients and logs safe for sensitive data.
- Expected latency and timeout behavior where an external provider is involved.

Each HTTP response includes an `X-Request-Id` UUID. The same value is attached to application log lines for that request; preserve it when reporting a failure. Only UUID-formatted caller values are accepted, and request bodies, credentials, and AI prompts must not be logged.

## Configuration Rules

- Non-secret defaults belong in version-controlled configuration.
- Secrets belong in a secret manager or encrypted deployment secret, never in source, images, logs, exports, or frontend assets.
- Environment-specific configuration must be explicit and validated at startup.
- Database URLs, object storage endpoints, queue endpoints, and AI provider credentials must be independently configurable.
- AI provider connection and request timeouts are configurable through `AI_CONNECT_TIMEOUT` and `AI_REQUEST_TIMEOUT`; keep the request timeout bounded and do not add blind retries to billable POST requests.
- Per-user AI request throttling is configurable with `AI_REQUESTS_PER_MINUTE`; tune it alongside the user's provider quotas and expected interview workflow.
- Local H2 and local filesystem storage are development/desktop options, not the public multi-user production default.

## Local Resume Migration

Run this once per deployment during a maintenance window, with application writes stopped, a verified database backup, the existing resume directory mounted at `RESUME_STORAGE_DIR`, and the destination S3-compatible bucket configured. The first invocation is read-only and checks source files:

```sh
java -jar target/offer-tracker-0.1.0.jar \
  --spring.profiles.active=mysql,production \
  --spring.main.web-application-type=none \
  --offer-tracker.resume-migration.enabled=true
```

Review the summary. To upload files and conditionally update database locators, repeat with `--apply`:

```sh
java -jar target/offer-tracker-0.1.0.jar \
  --spring.profiles.active=mysql,production \
  --spring.main.web-application-type=none \
  --offer-tracker.resume-migration.enabled=true \
  --apply
```

The command accepts only regular files under `RESUME_STORAGE_DIR`, rejects files over 20 MiB, processes records in bounded pages, and emits record IDs and exception types only. S3 keys are deterministic (`<S3_PREFIX>/legacy/<resume-id>.pdf`), so rerunning after an interrupted upload safely overwrites the same object. A database locator is updated only if it still matches the scanned local locator. Local files are deliberately retained; verify downloads against the destination provider and complete backups before any separate cleanup. A successful command is not proof of provider compatibility or a restore test.

## Legacy Ownership Cutover

Create or verify the target account out of band, stop application writes, back up MySQL, and run a dry-run first:

```sh
java -jar target/offer-tracker-0.1.0.jar \
  --spring.profiles.active=mysql,production \
  --spring.main.web-application-type=none \
  --offer-tracker.ownership-migration.enabled=true \
  --owner-id=123
```

Review the per-table counts, then repeat with `--apply` during the cutover window. The command only updates rows whose `owner_id` is still `NULL` in `companies`, `job_applications`, `resumes`, and `ai_interview_sessions`; it never changes already-owned records and never infers an owner from registration order. Verify authenticated access and the backup before reopening traffic.

## Release Gate

Do not enable open public registration until phone verification or an invitation-only policy is implemented, the production profile is verified with managed MySQL and Redis, trusted proxy CIDRs and network boundaries are configured, legacy ownership is reviewed and migrated where applicable, backups and object restoration are proven, alerts reach an operator, and staging load results establish an explicit supported workload. Keep the source environment and historical data intact during rehearsal and cutover.
