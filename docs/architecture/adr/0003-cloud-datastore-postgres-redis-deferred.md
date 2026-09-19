# ADR 0003: Cloud datastore PostgreSQL; Redis deferred for V1

- **Status:** Proposed
- **Date:** 2026-09-18
- **Related issues:** #2 (governing)

## Context

TinyAdmin Cloud needs durable storage for tenants, RBAC, connection metadata, Agent registry, command/outbox state, schema metadata cache, Action definitions, and audit history (≥1 year retention target). Product direction points at PostgreSQL for Cloud. Redis is commonly proposed for sessions, queues, and rate limits but adds operational surface.

## Decision

1. Use **PostgreSQL** as the sole primary datastore for TinyAdmin Cloud control-plane data in V1 (including sessions and command outbox/queue tables if needed).
2. **Defer Redis** for V1.
3. **Revisit Redis** when measurable needs appear, such as:
   - Distributed rate limiting across multiple Cloud instances
   - Hot-path caching that exceeds acceptable PostgreSQL load
   - Pub/sub fan-out that PostgreSQL LISTEN/NOTIFY or app-tier polling cannot meet cost-effectively

## Alternatives considered

1. **PostgreSQL + Redis from day one**
   - Deferred: extra failure domain and ops cost without proven V1 scale need; sessions/outbox fit PostgreSQL.
2. **MongoDB for Cloud control plane**
   - Rejected: Cloud stack and transactional authz+audit favor relational PostgreSQL; customer MongoDB is a *data-plane* target, not Cloud store.
3. **PostgreSQL only (chosen for V1)**
   - Simpler deploy; revisit criteria documented above.

## Consequences

- **Positive:** One datastore to back up, migrate, and reason about; strong fit for transactional audit + authz.
- **Negative:** Some rate-limit/cache patterns need careful SQL or app-tier design until Redis (or equivalent) is justified.
- **Follow-ups:** Schema and retention strategy for audit (≥1 year design target); outbox design coordinated with #3 delivery semantics.

## Related issues

- #2 — architecture
- #3 — command delivery / Agent protocol (may consume outbox rows)
