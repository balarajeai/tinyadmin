# TinyAdmin Architecture

Architecture documentation for TinyAdmin V1.

**Governing issue:** [#2 — Produce TinyAdmin V1 system architecture](https://github.com/balarajeai/tinyadmin/issues/2)

**Product lock:** [docs/product/v1-requirements.md](../product/v1-requirements.md)

## Documents

| Document | Purpose |
| --- | --- |
| [V1 System Architecture](./v1-system-architecture.md) | Control plane vs data plane, modular monolith module map, sequence flows, trust boundaries, risks, and acceptance criteria |
| [ADRs](./adr/) | Architecture Decision Records for major V1 choices |

## Architecture Decision Records (ADRs)

| ADR | Decision |
| --- | --- |
| [0001 — Modular monolith cloud](./adr/0001-modular-monolith-cloud.md) | Single Spring Boot deployable with package/module boundaries; reject premature microservices |
| [0002 — Control plane vs Agent data plane](./adr/0002-control-plane-vs-agent-data-plane.md) | Credentials stay customer-side; Agent outbound-only; Cloud never opens customer DB ports |
| [0003 — Cloud datastore Postgres; Redis deferred](./adr/0003-cloud-datastore-postgres-redis-deferred.md) | PostgreSQL for control-plane data; Redis deferred for V1 |
| [0004 — Agent technology Go](./adr/0004-agent-technology-go.md) | Recommend Go for the customer Agent |
| [0005 — Cloud stack Java / Spring](./adr/0005-cloud-stack-java-spring.md) | Confirm Java 21+ / Spring Boot for Cloud |

## Related issues

| Issue | Relationship |
| --- | --- |
| [#2](https://github.com/balarajeai/tinyadmin/issues/2) | This architecture deliverable (governing) |
| [#3](https://github.com/balarajeai/tinyadmin/issues/3) | Agent↔Cloud protocol / transport / authn / delivery semantics (owns at-least-once vs exactly-once) |
| [#6](https://github.com/balarajeai/tinyadmin/issues/6) | Domain model |
| [#4](https://github.com/balarajeai/tinyadmin/issues/4) / [#5](https://github.com/balarajeai/tinyadmin/issues/5) | Security threat modeling (consumes trust-boundary inputs from this design) |

## Status

Proposed for **Security + Code Review**. This package provides design inputs; it does **not** claim Security approval.
