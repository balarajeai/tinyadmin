# TinyAdmin architecture docs

Governing product lock: [`docs/product/v1-requirements.md`](../product/v1-requirements.md)  
Governing Architect issue: [#2](https://github.com/balarajeai/tinyadmin/issues/2)

## V1 system architecture

- [v1-system-architecture.md](./v1-system-architecture.md) — control plane vs Agent data plane, modular monolith module map, sequences, trust boundaries, risks, acceptance criteria
- Security trust-boundary inputs acknowledged (`SEC-PR10-001` … `SEC-PR10-004` CLEARED)
- Includes Code Review remediation for `CR-PR10-001` … `CR-PR10-005` (2026-09-19)

## ADRs

| ADR | Title |
| --- | --- |
| [0001](./adr/0001-modular-monolith-cloud.md) | Modular monolith for TinyAdmin Cloud |
| [0002](./adr/0002-control-plane-vs-agent-data-plane.md) | Control plane vs Agent data plane |
| [0003](./adr/0003-cloud-datastore-postgres-redis-deferred.md) | Cloud PostgreSQL; Redis deferred |
| [0004](./adr/0004-agent-technology-go.md) | Agent technology: Go |
| [0005](./adr/0005-cloud-stack-java-spring.md) | Cloud stack: Java 21+ / Spring Boot |
| [0006](./adr/0006-cloud-agent-security-properties.md) | Mandatory Cloud↔Agent security properties (#3 constraints) |

## Related follow-ons (not this package)

- Issue #3 — Agent↔Cloud protocol selection (must satisfy ADR 0006 / architecture §2.4)
- Issue #6 — Domain model
- Issues #4 / #5 — Security threat model and requirements
