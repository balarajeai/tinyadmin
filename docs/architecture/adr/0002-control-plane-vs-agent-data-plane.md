# ADR 0002: Control plane (Cloud) vs data plane (Agent)

- **Status:** Proposed (amended 2026-09-19 for Security SEC-PR10-001..004 inputs)
- **Date:** 2026-09-18
- **Related issues:** #2 (governing), #3 (protocol), #4 / #5 (Security)

## Context

Customer database credentials must not live in TinyAdmin Cloud. Customer Postgres/Mongo ports should not need public exposure. Support and ops users must perform Safe Actions and approved-field edits without raw SQL/Mongo or direct DB credentials. Product principles require an Agent in customer infrastructure with outbound connectivity to Cloud.

Security review of PR #10 (`SEC-PR10-001`) requires that architecture state **mandatory security properties** for Cloud↔Agent before Issue #3 selects mechanisms.

## Decision

Split responsibilities:

| Plane | Component | Owns |
| --- | --- | --- |
| **Control plane** | TinyAdmin Cloud | Identity, orgs/tenancy, RBAC, environment registry, connection *metadata* (no DB passwords), Action/approved-field definitions, authorization decisions, **Cloud-issued operation authorization bindings** for mutating Agent commands, audit store, Agent registry/command orchestration, cached non-secret schema metadata, UI APIs |
| **Data plane** | Customer Agent | Customer DB credentials (local secrets), live DB connections, schema discovery execution, record search/query execution, Action preview reads, Action execute/rollback mutations **after validating bindings**, outbound session to Cloud |

Hard rules:

1. Cloud **never** stores customer DB passwords/secrets.
2. Cloud **never** opens connections to customer Postgres/Mongo ports.
3. Agent initiates **outbound-only** encrypted sessions to Cloud.
4. Mutations are **Actions + approved-field edits only** — no arbitrary SQL/Mongo from Cloud or UI.
5. No artificial common query model across PostgreSQL and MongoDB.
6. Issue #3 **MUST** satisfy the normative property list in architecture §2.4 / [ADR 0006](./0006-cloud-agent-security-properties.md) (outbound-only, encrypted transport, authenticated Agent identity, command authenticity/integrity, replay resistance, organization binding, environment binding).
7. V1 Agents are bound to exactly one organization and one environment; see architecture §3.4.
8. Mutating commands carry Cloud-issued authorization bindings; see architecture §3.5.

**Owned by Issue #3 (not this ADR):** protocol encoding, handshake details, credential formats, and delivery guarantees (at-least-once vs exactly-once) — provided they meet the mandatory properties above.

## Alternatives considered

1. **Cloud-direct DB connections** (customer opens 5432/27017 or VPN tunnels into Cloud)
   - Rejected: violates credential-location and public-port product requirements; expands Cloud blast radius.
2. **Credentials stored encrypted in Cloud, decrypted for ephemeral Cloud→DB sessions**
   - Rejected for V1: credentials still leave customer infrastructure; hard Founder/Security exception required.
3. **Control plane + outbound Agent (chosen)**
   - Aligns with product lock; Security threat-models trust boundaries; #3 implements properties.
4. **Defer all Cloud↔Agent security properties to Issue #3 without a normative list**
   - Rejected after SEC-PR10-001: leads to insecure-by-default protocol choices.

## Consequences

- **Positive:** Credentials remain customer-side; no requirement to expose DB ports; clear Security trust-boundary list; Issue #3 has explicit conformance targets.
- **Negative:** Agent availability becomes a dependency for discovery/search/actions; command delivery and idempotency must be designed carefully (#3); Agents cannot span environments in V1 without a new ADR.
- **Follow-ups:** Issue #3 ADR must map each §2.4 property to a mechanism; Security (#4/#5) verifies; domain model (#6) stores immutable org/env ownership.

## Related issues

- #2 — architecture
- #3 — Agent↔Cloud protocol
- #4 / #5 — Security threat model / requirements
