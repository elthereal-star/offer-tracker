# ADR 0001: Evolve As A Modular Monolith Before Splitting Services

- Status: Accepted
- Date: 2026-10-01

## Context

The project currently contains a Spring Boot application with company, application, interview, resume, analytics, data transfer, and AI interview modules. The intended product may eventually serve a large number of registered users, but the current codebase has no authentication, enforced ownership, shared file storage, or asynchronous AI task system.

Splitting the current code into network services before these contracts are stable would multiply deployment, data consistency, and observability problems. A single deployable application can still scale horizontally when it has explicit module interfaces and no process-local business state.

## Decision

Keep one deployable Spring Boot application and organize it as a modular monolith. Each domain module owns its application logic and persistence adapter. Cross-module calls go through narrow interfaces; modules do not modify another module's tables directly.

The production deployment will make the application stateless and run multiple instances behind a load balancer. MySQL, object storage, Redis, and the AI task system are infrastructure adapters behind explicit seams.

## Consequences

Positive:

- One release artifact and one initial operational surface.
- Lower migration risk from the current project.
- Module-level tests can be introduced before any service extraction.
- A later split can happen at a proven seam instead of an assumed one.

Costs:

- Module boundaries must be enforced by code review and tests.
- A single application still requires disciplined database migrations.
- Some modules may eventually need independent scaling and can then be extracted.

## Rejected Alternative

Starting with microservices would add network contracts, distributed transactions, service discovery, and separate deployments before the product has stable identity, ownership, and workload requirements.

