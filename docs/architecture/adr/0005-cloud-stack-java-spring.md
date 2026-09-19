# ADR 0005: Cloud stack — Java 21+ / Spring Boot

- **Status:** Proposed
- **Date:** 2026-09-18
- **Related issues:** #2 (governing), #6 (domain model)

## Context

Product and engineering direction for TinyAdmin Cloud favors a JVM backend with strong typing, mature security libraries, and transactional persistence. V1 auth is email/password + invites + sessions + password reset; architecture must not block future OIDC/SAML SSO.

## Decision

Confirm **Java 21+** and **Spring Boot** for TinyAdmin Cloud (modular monolith per ADR 0001), with **PostgreSQL** as the primary datastore (ADR 0003).

Identity module guidance:

- Implement V1 email/password, invites, sessions, and password reset behind clear interfaces
- Keep authentication/session abstractions **SSO-ready** (OIDC/SAML later) without baking enterprise IdP assumptions into every module
- Enforce RBAC and tenancy **server-side** on every mutating and sensitive read path

## Alternatives considered

1. **Node/TS or Go for Cloud**
   - Rejected for V1 Cloud: product direction and team skills favor Java/Spring; Agent is Go (ADR 0004).
2. **Quarkus / Micronaut instead of Spring Boot**
   - Deferred: Spring Boot is the default ecosystem choice unless startup/footprint constraints appear in Contabo deployment work (out of scope for this ADR).
3. **Java 21+ / Spring Boot (chosen)**
   - Aligns with product direction; SSO-later-friendly identity design.

## Consequences

- **Positive:** Consistent Cloud stack; Spring Security / Data / Transaction patterns fit modular monolith + audit needs.
- **Negative:** JVM ops characteristics must be sized for Contabo later (not decided here).
- **Follow-ups:** Domain model (#6); Security review of authn/session and tenant filters (#4/#5).

## Related issues

- #2 — architecture
- #6 — domain model
- #4 / #5 — Security
