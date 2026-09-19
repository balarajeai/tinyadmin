# ADR 0001: Modular monolith for TinyAdmin Cloud

- **Status:** Proposed
- **Date:** 2026-09-18
- **Related issues:** #2 (governing), #6 (domain model)

## Context

TinyAdmin Cloud must provide identity, multi-tenant organizations, RBAC, environment-scoped connections, Agent orchestration, discovery/search orchestration, Safe Actions, and an immutable audit trail. Product requirements lock a modular monolith unless Architect documents a concrete reason otherwise. A small team will implement V1; authz decisions and audit writes need shared transactional consistency.

## Decision

Ship TinyAdmin Cloud as a **single Spring Boot deployable** organized as a **modular monolith** with explicit package / bounded-context modules (`identity`, `tenancy`, `rbac`, `environments`, `connections`, `agentcontrol`, `discovery`, `records`, `actions`, `audit`, `shared`).

Extract a module into a separate deployable **only** with a written justification covering team capacity, transactional/consistency cost, operational overhead, and security surface — not by default.

## Alternatives considered

1. **Microservices from day one** (auth-service, user-service, audit-service, notification-service, agent-service, etc.)
   - Rejected for V1: unjustified network cost, distributed transaction complexity for authz+audit, operational burden for a small team, premature boundary freezing.
2. **Big-ball-of-mud monolith** (no module boundaries)
   - Rejected: blocks Code Review and Security review of isolation; invites cross-tenant bugs.
3. **Modular monolith with extract-on-demand**
   - Chosen: clear boundaries now; extraction path later if evidence warrants.

## Consequences

- **Positive:** Shared transactions for authorization + audit; simpler local development and deployment; clear Code Review unit (module dependency rules).
- **Negative:** Discipline required to keep module boundaries; risk of accidental coupling without package/dependency checks.
- **Follow-ups:** Module dependency rules and acceptance criteria live in [v1-system-architecture.md](../v1-system-architecture.md). Domain entities refined in #6.

## Related issues

- #2 — architecture deliverable
- #6 — domain model
