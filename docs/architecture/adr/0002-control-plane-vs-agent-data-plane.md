# ADR 0002: Control plane (Cloud) vs data plane (Agent)

- **Status:** Proposed
- **Date:** 2026-09-18
- **Related issues:** #2 (governing), #3 (protocol), #4 / #5 (Security)

## Context

Customer database credentials must not live in TinyAdmin Cloud. Customer Postgres/Mongo ports should not need public exposure. Support and ops users must perform Safe Actions and approved-field edits without raw SQL/Mongo or direct DB credentials. Product principles require an Agent in customer infrastructure with outbound connectivity to Cloud.

## Decision

Split responsibilities:

| Plane | Component | Owns |
| --- | --- | --- |
| **Control plane** | TinyAdmin Cloud | Identity, orgs/tenancy, RBAC, environment registry, connection *metadata* (no DB passwords), Action/approved-field definitions, authorization decisions, audit store, Agent registry/command orchestration, cached non-secret schema metadata, UI APIs |
| **Data plane** | Customer Agent | Customer DB credentials (local secrets), live DB connections, schema discovery execution, record search/query execution, Action preview reads, Action execute/rollback mutations, outbound session to Cloud |

Hard rules:

1. Cloud **never** stores customer DB passwords/secrets.
2. Cloud **never** opens connections to customer Postgres/Mongo ports.
3. Agent initiates **outbound-only** encrypted sessions to Cloud.
4. Mutations are **Actions + approved-field edits only** — no arbitrary SQL/Mongo from Cloud or UI.
5. No artificial common query model across PostgreSQL and MongoDB.

Transport, Agent authentication, and delivery semantics (at-least-once vs exactly-once) are owned by **Issue #3**, not this ADR.

## Alternatives considered

1. **Cloud-direct DB connections** (customer opens 5432/27017 or VPN tunnels into Cloud)
   - Rejected: violates credential-location and public-port product requirements; expands Cloud blast radius.
2. **Credentials stored encrypted in Cloud, decrypted for ephemeral Cloud→DB sessions**
   - Rejected for V1: credentials still leave customer infrastructure; hard Founder/Security exception required.
3. **Control plane + outbound Agent (chosen)**
   - Aligns with product lock; Security threat-models trust boundaries next.

## Consequences

- **Positive:** Credentials remain customer-side; no requirement to expose DB ports; clear Security trust-boundary list.
- **Negative:** Agent availability becomes a dependency for discovery/search/actions; command delivery and idempotency must be designed carefully (#3).
- **Follow-ups:** Security (#4/#5) threat-models Internet↔Cloud, Cloud↔Agent, Agent↔DB, tenant/environment isolation, and audit integrity.

## Related issues

- #2 — architecture
- #3 — Agent↔Cloud protocol
- #4 / #5 — Security threat model
