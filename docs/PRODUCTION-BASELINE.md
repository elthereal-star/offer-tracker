# Production Baseline

This document records the rules for the productionization work. It is a baseline and checklist, not a claim that all items are already implemented.

## Module Map

| Module | Owns | Current status |
| --- | --- | --- |
| Identity | Users, credentials, sessions, roles | Planned; no authentication yet |
| Company | Company records and company search | Implemented for local use |
| Application | Job applications and status changes | Implemented for local use |
| Interview | Manual interview rounds and results | Implemented for local use |
| Resume | PDF metadata, extracted text, file lifecycle | Implemented with local storage |
| AI Interview | Sessions, questions, answers, scoring, reports | Implemented synchronously |
| AI Provider | OpenAI-compatible configuration and client adapter | Implemented with local file configuration |
| Analytics | Aggregated application statistics | Implemented for local use |
| Data Transfer | JSON backup, restore, and CSV export | Implemented for local use |

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

