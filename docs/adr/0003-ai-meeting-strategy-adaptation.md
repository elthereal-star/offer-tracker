# ADR 0003: AI-Meeting Strategy Adaptation

- Status: Accepted
- Scope: `ai-interview` enhancement branch
- Date: 2026-10-04

## Decision

Adapt the reliability strategies from AI-Meeting behind Offer Tracker's existing
interfaces. MySQL remains the authoritative store for user, application, resume,
interview, and AI task data. Redis is used for shared sessions, limits, task
delivery, and runtime coordination. MongoDB is optional and is reserved for
long-form conversation/archive data; it is never required by the lightweight
profile.

The migration order is:

1. Explicit AI interview state transitions, request deduplication, finalization
   locking, and bounded retry/error semantics.
2. Redis-backed single-flight and runtime snapshots with lazy rehydration from
   MySQL when Redis state is missing.
3. Optional Mongo archive, SSE streaming, WebSocket speech input, message
   sequencing, and provider-specific handlers.

## Adaptation matrix

| AI-Meeting strategy | Offer Tracker adaptation | Availability |
| --- | --- | --- |
| Redis + MySQL + Mongo runtime layers | MySQL authority, Redis coordination, optional Mongo archive | production profile / optional Mongo |
| Single-flight | Same-key AI request result sharing; local fallback for H2 | enabled without new dependency |
| EnumMap state machine | AI session lifecycle transitions;投递状态 remains user-adjustable | enabled |
| Duplicate-submit lock | Session/question/finalize keys | Redis production, local lock fallback |
| Hot/cold runtime snapshots | AI question/answer progress rehydration | production phase |
| SSE/WebSocket | streaming AI output and optional text/transcript input | `realtime` profile |
| Message sequence allocator | per-session AI event ordering | streaming phase |
| Repair/retry/dead letter | existing AI task outbox and worker recovery | already present, to be extended |
| Provider handler factory | OpenAI-compatible adapter plus future providers | incremental |
| Error envelope, ownership, masking, rate limits | existing API/security layers | already present, to be tightened |

## Non-goals

- Do not make MongoDB, a speech provider, or a third-party SMS provider
  mandatory for local use.
- Do not claim exactly-once provider execution or duplicate-billing prevention.
- Do not replace the user's free-form application status model with a rigid
  workflow; only the AI interview lifecycle is constrained.
