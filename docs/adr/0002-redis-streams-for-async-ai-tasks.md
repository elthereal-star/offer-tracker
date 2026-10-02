# ADR 0002: Use Redis Streams For Asynchronous AI Tasks

- Status: Accepted for the first production phase
- Date: 2026-10-02

## Context

AI generation and scoring currently run synchronously inside HTTP requests. Provider latency, transient failures, and concurrent work can exhaust servlet threads and make retries ambiguous. The production profile already requires Redis for shared rate limiting, so a first asynchronous task system should avoid adding another mandatory service.

## Decision

Use Redis Streams as the initial task transport behind an application-owned task interface. Persist the task state in MySQL and use the stream only as a delivery mechanism. Every task has an owner, operation type, idempotency key, attempt count, lease expiry, and terminal result or error. Consumers acknowledge a message only after the MySQL state transition and provider operation have been durably recorded. Expired leases are eligible for bounded redelivery; exhausted attempts move to a dead-letter stream and a terminal `FAILED` state.

The HTTP API submits a task and returns a task identifier. Clients poll a read-only task status endpoint. Existing synchronous endpoints remain available during the migration and are not silently changed to fire-and-forget.

## Consequences

Positive:

- Reuses the existing Redis dependency and supports horizontal consumers.
- MySQL remains the source of truth for ownership, status, and audit history.
- Explicit idempotency and leases prevent duplicate provider billing during normal retries.
- RabbitMQ or Kafka can replace the transport behind the same task interface later.

Costs and limits:

- The application must implement consumer groups, claiming, leases, redelivery, and dead-letter handling.
- Redis persistence and memory policy become part of the task durability review.
- This is not a replacement for a dedicated workflow engine or a guarantee against provider-side duplicate billing.

## Rejected alternatives

- RabbitMQ now: stronger queue-native retry features, but adds a new mandatory operational dependency before workload measurements justify it.
- Kafka now: useful for very high throughput, but operationally disproportionate to the current AI workload.
- In-process executor: cannot provide durable work or safe horizontal scaling.

## Exit criteria for implementation

- MySQL task migration and owner-scoped repository.
- Redis Stream publisher and consumer group bootstrap.
- Idempotent submission, bounded retries, lease recovery, and dead-letter handling.
- Status endpoint and metrics for queue depth, age, retries, failures, and provider latency.
- Integration tests with MySQL and Redis, plus a provider-mocked duplicate-delivery test.
