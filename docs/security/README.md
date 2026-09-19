# TinyAdmin Security Documentation

Security Engineer artifacts for TinyAdmin V1. These documents are **inputs and gates** for architecture reconciliation and implementation — they do **not** claim the running system is secure.

**Governing issues:** [#4](https://github.com/balarajeai/tinyadmin/issues/4) (threat model — **primary this package**), [#5](https://github.com/balarajeai/tinyadmin/issues/5) (security requirements — companion draft)  
**Consumes:** Founder [V1 product requirements](../../product/v1-requirements.md), MERGED [V1 system architecture](../../architecture/v1-system-architecture.md) + ADR 0001–0006  
**Related:** [#2](https://github.com/balarajeai/tinyadmin/issues/2) architecture (inputs acknowledged), [#3](https://github.com/balarajeai/tinyadmin/issues/3) Agent↔Cloud protocol (PENDING mechanisms)

---

## Index

| Document | Purpose | Issue |
| --- | --- | --- |
| [v1-threat-model.md](./v1-threat-model.md) | Formal V1 narrative: assets, actors, trust boundaries, surfaces, gates, residuals, #3 pending | **#4** |
| [threat-register.md](./threat-register.md) | Actionable TM-* register (exact fields) | **#4** |
| [fa-conn-blast.md](./fa-conn-blast.md) | FA-CONN-BLAST disposition — not silently accepted | **#4** |
| [issue-3-security-handoff.md](./issue-3-security-handoff.md) | Properties #3 must satisfy + conformance template | **#4 → #3** |
| [implementation-handoffs.md](./implementation-handoffs.md) | Backend / Agent / Frontend / QA / Platform requirements | **#4** |
| [v1-security-requirements.md](./v1-security-requirements.md) | SHALL requirements (copied from PR #11) — **Issue #5 companion still draft**; Architect enforceability + QA testability gates open | **#5** |
| [reviews/pr-10-architecture-security-review.md](./reviews/pr-10-architecture-security-review.md) | Historical Security review of PR #10 (Issue #2) | #2 / PR #10 |
| [reviews/issue-4-threat-model-status.md](./reviews/issue-4-threat-model-status.md) | Issue #4 status: architecture blockers cleared; TM supersedes prior draft | **#4** |

---

## Status (Issue #4)

| Artifact | Status |
| --- | --- |
| Threat model narrative | **Formal V1** — Architect feasibility + QA testability review required |
| Threat register | **Formal V1** |
| FA-CONN-BLAST | Disposition written; **Founder decision open** (ACCEPT/REJECT/DEFER) |
| Issue #3 handoff | Ready for Architect; Security re-review when #3 ADR lands |
| Implementation handoffs | Requirements only — not Sprint 1 work |
| Security requirements (Issue #5) | Draft companion from prior PR #11 — **not** closed by Issue #4 |

Architecture trust-boundary inputs SEC-PR10-001..004 were **CLEARED** on MERGED main (PR #10). This Issue #4 package **supersedes** the earlier PR #11 draft threat-model narrative with register/FA/#3/impl split aligned to ADR 0006.

---

## Hard product constraints (Founder)

Treated as non-negotiable unless Founder + Security explicitly accept an exception:

1. No customer DB credentials in TinyAdmin Cloud  
2. Credentials remain in customer-controlled infrastructure  
3. Agent initiates outbound encrypted communication to Cloud  
4. Customers must not need public DB ports for TinyAdmin  
5. Support/ops never receive customer DB credentials through TinyAdmin  
6–7. No arbitrary SQL / Mongo mutations  
8. Mutations only via Safe Actions or configured approved-field edits  
9. Server-side authorization for sensitive operations  
10. Tenant isolation mandatory; **cross-tenant = CRITICAL**  
11. Prod/staging must not silently cross  
12–13. Preview never mutates; must not overclaim authority  
14–15. Rollback only when safely reversible; authorized + audited  
16–18. Mutation audit; ops cannot alter/delete audit history; retention ≥1 year  

---

## Issue #3 note

Security defines **required security properties** (ADR 0006 / architecture §2.4 + this TM). Protocol selection is **Architect-owned** in Issue #3 among options that satisfy those properties. See [issue-3-security-handoff.md](./issue-3-security-handoff.md).

---

## Ownership

| Concern | Owner |
| --- | --- |
| Architecture / Issue #3 protocol | Architect |
| Threat model / FA text / Security gates | **Security Engineer** |
| Founder acceptance (FA-*) | Founder / Product Owner |
| Implementation of controls | Backend / Agent / Frontend / Platform |
| Testability evidence | QA |
