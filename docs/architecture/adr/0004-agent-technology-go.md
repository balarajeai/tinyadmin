# ADR 0004: Agent technology — Go

- **Status:** Proposed
- **Date:** 2026-09-18
- **Related issues:** #2 (governing), #3 (protocol implementation)

## Context

The TinyAdmin Agent runs in customer infrastructure: holds local DB secrets, maintains live Postgres/Mongo connections, executes discovery/search/preview/execute/rollback, and maintains an outbound session to Cloud. It should be a low-footprint, reliable long-running process suitable for ops deployment (single binary preferred).

## Decision

**Recommend Go** for the V1 Agent.

Rationale:

- Single static (or near-static) binary simplifies customer install
- Low memory/CPU footprint for always-on Agent
- Strong concurrency model for multiplexed connections and command handling
- Mature drivers: `pgx` for PostgreSQL; official/community MongoDB Go driver
- Good fit for outbound long-lived connections and structured logging without heavy runtime

## Alternatives considered

1. **Node / TypeScript**
   - Pros: shared language with a potential frontend/API culture; fast iteration.
   - Cons: larger runtime footprint; packaging/distribution heavier; weaker “single binary” story; concurrency model less ideal for long-lived multiplexed DB work.
   - **Rejected / deferred** for Agent V1.
2. **Java**
   - Pros: shared language/tooling with Cloud (Spring).
   - Cons: heavier runtime for a customer-side Agent; JVM footprint and packaging friction for small SaaS customers.
   - **Rejected / deferred** for Agent V1 (Cloud remains Java).
3. **Go (chosen)**
   - Best balance of footprint, concurrency, and driver ecosystem for this role.

## Consequences

- **Positive:** Clear Agent tech recommendation; Cloud and Agent languages may differ (acceptable for plane split).
- **Negative:** Two language ecosystems to maintain; shared DTOs/contracts need language-neutral schemas (owned with #3).
- **Follow-ups:** Issue #3 defines wire protocol and authn; Agent implements against that ADR.

## Related issues

- #2 — architecture
- #3 — protocol / transport
