# Implementation Handoffs (from Issue #4 Threat Model)

**Status:** Requirements handoff — **not** Sprint 1 task breakdown  
**Date:** 2026-09-19 (America/Chicago)  
**Source:** [v1-threat-model.md](./v1-threat-model.md), [threat-register.md](./threat-register.md), [fa-conn-blast.md](./fa-conn-blast.md)  
**Note:** Issue #3 owns protocol mechanisms; do not invent weaker transport/authn in feature PRs.

---

## Backend (Cloud)

Must implement / enforce:

| Area | Requirements (from TM) | Primary threats / gates |
| --- | --- | --- |
| Tenant isolation | Server-side org from session membership; never trust client `organizationId`; deny-by-default on all tenant-owned resources | TM-TEN-001..004; SG-2 |
| Environment isolation | Immutable connection org/env; Agent association org/env match; commands carry env; prod distinguishable in API | TM-ENV-*; SG-3 |
| RBAC | Server-side on every sensitive call; **define ≠ run** for Actions/approved-fields | TM-RBAC-*; SG-7 |
| Authn/sessions/invites/reset | Hashing, rate limits, secure cookies, CSRF, single-use invite/reset TTLs, session revoke | TM-AUTH-*, TM-UI-002; SG-1 |
| Secrets | **Never** accept/store/log customer DB passwords; redact logs/errors/audit | TM-SEC-001..002; SG-9/10 |
| Actions orchestration | Confirm gate before minting §3.5 binding; preview honesty flags; operation lifecycle pending/succeeded/failed/unknown; no silent terminalization | TM-ACT-004..008; SG-5/6 |
| Authz bindings | Mint bindings with §3.5 minimum fields only after RBAC+confirm; revoke/invalidate on permission/Agent/connection revoke | TM-AG-004, TM-Q-001; SG-5 |
| Audit | Append every mutation attempt (incl. fail/unknown); storage-level immutability; ops cannot update/delete; retention ≥1 year design | TM-AUD-*; SG-8 |
| Rollback | Only if Action declares reversible; authorize+audit; link original `operation_id` | TM-RB-001; SG-5 |
| Queues | TTL/depth limits; cancel-on-revoke; scope by agent+org+env | TM-Q-*; SG-11 |
| Enrollment | Short-lived single-use org+env pre-bound proofs; immutable Agent org/env after activation | TM-ENR-*; SG-4 |
| Blast / FA | Support revoke-all + binding invalidation; do not ship multi-conn prod without FA-CONN-BLAST decision + CC evidence | TM-CONN-002; SG-12 |
| SSRF | No user-driven Cloud URL fetch features in V1 without Security review | TM-SSRF-001 |

Do **not** implement Agent↔Cloud wire protocol ad hoc — consume Issue #3 ADR.

---

## Agent

Must implement / enforce:

| Area | Requirements | Primary threats / gates |
| --- | --- | --- |
| Reject duties | Authenticity/integrity/freshness; org/env/agent/connection/Action binding checks; reject mismatches | TM-AG-002..004, TM-CONN-001, TM-ENV-001; SG-4/5 |
| Credentials | Local secrets only; file permissions / secret-store guidance; never send DB passwords to Cloud | TM-SEC-001/003; SG-10 |
| Queries | Parameterized driver APIs only; no arbitrary SQL/Mongo execution paths | TM-INJ-*; SG-10 |
| Preview | Read-only at DB boundary; return limitation/non-authoritative flags | TM-ACT-003..004; SG-6 |
| Execute | Validate §3.5 binding; execute-time revalidation or safe preview bind; abort unsafe TOCTOU | TM-ACT-005; SG-5 |
| Idempotency | Dedupe mutating work by `operation_id`; safe under at-least-once | TM-ACT-007; SG-5 |
| Results | Durably retain + retry until Cloud ack; honest outcomes | TM-ACT-008; SG-5 |
| Rollback | Re-check reversibility/safety; require binding | TM-RB-001; SG-5 |
| Connection readiness | Report not-ready when local secret missing; refuse ops for unknown connection_id | TM-CONN-001/003 |
| Blast | Per-connection credential usage; support revoke; no secret logging | FA-CONN-BLAST CC-*; SG-12 |
| Bounds | Enforce Action affected-record caps | TM-ACT-002 |

Protocol crypto/session details: per Issue #3 only.

---

## Frontend

Must implement / verify:

| Area | Requirements | Primary threats / gates |
| --- | --- | --- |
| XSS | Encode untrusted record/Action/field strings; CSP baseline | TM-UI-001; SG-1 |
| Active org/env | Clear active organization and environment; production visually/operationally distinct | TM-TEN-004, TM-ENV-001; SG-3 |
| Confirm UX | Distinct confirm from preview; surface limitation/staleness flags; require ack when flags present | TM-ACT-004/006; SG-6 |
| Rollback UX | Do not show rollback when unavailable/unsafe | TM-RB-001 |
| No secret fields | Connection UI never collects DB passwords for Cloud POST | TM-SEC-001 |
| RBAC UX | Hide is not authz; still call APIs that enforce server-side | TM-RBAC-002 |
| CSRF-safe patterns | Follow backend cookie/CSRF contract | TM-UI-002 |

---

## QA

Must eventually verify (evidence for Security gates):

| Gate | Evidence examples |
| --- | --- |
| SG-2 | Cross-org IDOR suite on connections, Agents, Actions, search, audit, discovery |
| SG-3 | Staging Action cannot execute on prod connection; Agent reject; UI distinction screenshots/API flags |
| SG-4 | Against #3: forged/replayed/expired commands rejected; enrollment reuse fails |
| SG-5 | Binding mismatch reject; double delivery single mutate; pending/unknown honesty when results dropped |
| SG-6 | Preview causes zero DB writes; flags appear at confirm |
| SG-7 | Define vs run permission matrix tests; non-allowlisted field mutate denied |
| SG-8 | App DB role cannot UPDATE/DELETE audit; fail/unknown paths audited |
| SG-9 | Secret-in-error redaction tests |
| SG-10 | Cloud DB/API contains no customer DB passwords |
| SG-11 | Revoke then reconnect → queued mutate non-executable; depth limit |
| SG-12 | Revoke+invalidate; connection_id mismatch; docs/runbook presence; FA recorded or topology reject |

CRITICAL/HIGH failures block release unless Founder exception recorded.

---

## Platform

Must plan / provide:

| Area | Requirements |
| --- | --- |
| TLS / hosting | Correct TLS termination for Cloud; no cleartext Agent control path |
| Secrets at rest | Cloud encryption for Agent credentials/session material; backup access control (TM-SEC-004) |
| Rate limits | Auth and mutation rate limiting (Redis deferred — ADR 0003 revisit under pressure) |
| Runbooks | Agent host compromise (FA-CONN-BLAST CC-4); enrollment revoke; audit export ACLs |
| Insider controls | Limited superuser; break-glass logging (TM-HST-003 / R4) |
| Supply chain | Dependency review for Cloud/Agent when implementation starts |

Production infrastructure provisioning remains out of Issue #4 scope; Platform owns future hardening evidence.

---

## Architect (ongoing)

- Own Issue #3 mechanism selection using [issue-3-security-handoff.md](./issue-3-security-handoff.md)
- Do not weaken §2.4 / §3.4–3.5 / §5.6.1 in feature ADRs
- Feasibility review of this TM (Issue #4 gate)

---

## Document history

| Date | Change |
| --- | --- |
| 2026-09-19 | Initial handoffs from formal Issue #4 TM |
