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

- Set `AUTH_REQUIRED=true` for public deployments. It defaults to `false` for desktop/local compatibility.
- Provide independent secrets `JWT_SECRET` and `AI_CONFIG_ENCRYPTION_KEY`, each at least 32 characters. The encryption key must be backed up and kept stable; rotating it requires re-encrypting stored AI credentials.
- Use the MySQL profile and a managed MySQL service. The default profile remains local H2.
- Resume files still use local disk and are not safe for multi-instance deployments; object storage is not implemented yet.
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
