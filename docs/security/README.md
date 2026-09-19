# TinyAdmin Security Documentation

Security Engineer artifacts for TinyAdmin V1. These documents are **inputs and gates** for architecture reconciliation and implementation — they do not claim the running system is secure.

**Governing issues:** [#4](https://github.com/balarajeai/tinyadmin/issues/4) (threat model), [#5](https://github.com/balarajeai/tinyadmin/issues/5) (security requirements)  
**Consumes:** [V1 product requirements](../../product/v1-requirements.md), [V1 system architecture](../../architecture/v1-system-architecture.md) (PR #10)  
**Related:** [#2](https://github.com/balarajeai/tinyadmin/issues/2) architecture, [#3](https://github.com/balarajeai/tinyadmin/issues/3) Agent↔Cloud protocol

## Index

| Document | Purpose | Issue |
| --- | --- | --- |
| [v1-threat-model.md](./v1-threat-model.md) | V1 assets, actors, trust boundaries, threats, mitigations, residual risks, Security gates | #4 |
| [v1-security-requirements.md](./v1-security-requirements.md) | Testable SHALL requirements for authn/authz, Agent, Safe Actions, preview/rollback, audit, secrets | #5 |
| [reviews/pr-10-architecture-security-review.md](./reviews/pr-10-architecture-security-review.md) | Independent Security review of PR #10 architecture package (Issue #2 trust-boundary inputs only) | #2 / PR #10 |

## Status

| Artifact | Status |
| --- | --- |
| Threat model | Draft — Architect feasibility review required |
| Security requirements | Draft — Architect enforceability + QA testability review required |
| PR #10 review | Complete as draft — see review verdict; Architect must address BLOCKING findings |

## Hard product constraints (Founder)

See `docs/product/v1-requirements.md`. Security artifacts treat these as non-negotiable unless Founder + Security explicitly accept an exception:

1. No customer DB credentials in TinyAdmin Cloud  
2. Credentials remain in customer-controlled infrastructure  
3. Agent initiates outbound encrypted communication to Cloud  
4. Customers must not need public DB ports for TinyAdmin  
5. Support/ops never receive customer DB credentials through TinyAdmin  
6–7. No arbitrary SQL / Mongo mutations  
8. Mutations only via Safe Actions or configured approved-field edits  
9. Server-side authorization for sensitive operations  
10. Tenant isolation mandatory; cross-tenant = CRITICAL  
11. Prod/staging must not silently cross  
12–13. Preview never mutates; must not overclaim authority  
14–15. Rollback only when safely reversible; authorized + audited  
16–18. Mutation audit; ops cannot alter/delete audit history; retention ≥1 year  

## Issue #3 note

Security defines **required security properties** for Agent↔Cloud transport and authn. Protocol selection (mTLS vs signed JWT device credentials vs other) is **Architect-owned** in Issue #3, among options that satisfy those properties.
