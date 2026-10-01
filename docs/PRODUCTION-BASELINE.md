# Production Baseline

This document records the rules for the productionization work. It is a baseline and checklist, not a claim that all items are already implemented.

## Module Map

| Module | Owns | Current status |
| --- | --- | --- |
| Identity | Users, credentials, sessions, roles | Phone registration/login, JWT access tokens, refresh rotation; production enforcement configurable |
| Company | Company records and company search | Owner-scoped when authenticated; anonymous compatibility mode remains for local use |
| Application | Job applications and status changes | Owner-scoped when authenticated; anonymous compatibility mode remains for local use |
| Interview | Manual interview rounds and results | Ownership checked through the parent application |
| Resume | PDF metadata, extracted text, file lifecycle | Owner-scoped metadata; files still use local filesystem storage |
| AI Interview | Sessions, questions, answers, scoring, reports | Owner-scoped synchronous sessions |
| AI Provider | OpenAI-compatible configuration and client adapter | User-scoped AES-GCM encrypted credentials; anonymous local mode retains file config |
| Analytics | Aggregated application statistics | Owner-scoped when authenticated |
| Data Transfer | JSON backup, restore, and CSV export | Owner-scoped when authenticated |

## Current Production Gaps

- Start public deployments with `--spring.profiles.active=mysql,production`. The `production` profile requires DB credentials and both independent secrets, and forces authentication unless explicitly overridden.
- Provide independent secrets `JWT_SECRET` and `AI_CONFIG_ENCRYPTION_KEY`, each at least 32 characters. The encryption key must be backed up and kept stable; rotating it requires re-encrypting stored AI credentials. The fallback values in the default profile are local-only and must never be used publicly.
- Complete the legacy ownership cutover before enabling public access: records created before owner IDs were introduced have `owner_id IS NULL`, and authenticated queries intentionally hide them. Assign historical rows to the verified migration account in a controlled maintenance step; never auto-claim them for the first registrant.
- Registration is not production-ready until a cloud `SmsCodeSender` adapter is selected and supplied with the `sms-cloud` profile. The local logging adapter is disabled under `production`; without a provider the verification-code endpoint returns 503 and does not log codes.
- Use a managed MySQL service. The default profile remains local H2.
- Resume files can use the S3-compatible adapter for multi-instance deployments; configure the provider endpoint, region, and bucket, and provide credentials through the AWS SDK default credential chain. Verify the chosen provider's compatibility, access policy, lifecycle, and backup behavior before go-live.
- Existing resume files have local filesystem locators. Before switching an existing installation to S3, migrate each object and update its database locator during a controlled cutover; do not change `RESUME_STORAGE_TYPE` alone. A resumable migration utility and provider-specific live compatibility test remain to be built.
- Redis-backed rate limiting/session coordination, asynchronous AI jobs, observability, automated restore drills, and capacity testing remain future phases.

## Required Production Interfaces

Every externally reachable interface must define:

- Authentication and authorization requirements.
- Resource ownership checks.
- Input limits and validation rules.
- Pagination and ordering behavior.
- Idempotency behavior for retryable writes.
- Error codes safe for clients and logs safe for sensitive data.
- Expected latency and timeout behavior where an external provider is involved.

## Configuration Rules

- Non-secret defaults belong in version-controlled configuration.
- Secrets belong in a secret manager or encrypted deployment secret, never in source, images, logs, exports, or frontend assets.
- Environment-specific configuration must be explicit and validated at startup.
- Database URLs, object storage endpoints, queue endpoints, and AI provider credentials must be independently configurable.
- Local H2 and local filesystem storage are development/desktop options, not the public multi-user production default.

## Phase 0 Exit Criteria

- `CONTEXT.md` contains the canonical domain terms and invariants.
- ADR 0001 records the modular-monolith decision and its trade-offs.
- This baseline identifies current capabilities separately from planned production work.
- The next phase can implement identity and ownership without redefining domain vocabulary.
