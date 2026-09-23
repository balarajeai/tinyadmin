# Issue #3 Security Handoff — Required properties (no mechanism selection)

**From:** Security Engineer (Issue #4 threat model)  
**To:** Architect (Issue #3 — Agent↔Cloud protocol ADR)  
**Date:** 2026-09-19 (America/Chicago)  
**Status:** Normative checklist for #3; Security will **re-review** #3 against this handoff + [v1-threat-model.md](./v1-threat-model.md) + ADR 0006

Security does **not** select mTLS, signed JWT, or any other mechanism. Issue #3 selects mechanisms that satisfy **all** properties below.

---

## 1. Mandatory properties (ADR 0006 / architecture §2.4)

| ID | Property | Requirement | Related threats |
| --- | --- | --- | --- |
| P-OUTBOUND | Outbound-only | Agent initiates Cloud connectivity; no customer inbound Agent control ports | TM-AG-006 |
| P-ENCRYPT | Encrypted transport | All control traffic encrypted (TLS or equivalent); cleartext forbidden | TM-AG-005 |
| P-AGENT-ID | Authenticated Agent identity | Cloud authenticates a **specific enrolled Agent**; org-wide static secret alone insufficient | TM-AG-001, TM-ENR-001 |
| P-CMD-AUTH | Command authenticity/integrity | Agent can detect forged or tampered commands | TM-AG-002 |
| P-REPLAY | Replay resistance | Sensitive/mutating commands resist replay (nonce/jti/sequence/expiry or equivalent); silent re-mutation forbidden | TM-AG-003, TM-ACT-007 |
| P-ORG-BIND | Organization binding | Enrollment, session, and sensitive commands bound to one org; Agent rejects mismatch | TM-TEN-*, TM-Q-003 |
| P-ENV-BIND | Environment binding | Enrollment/activation and sensitive commands bound to one env; Agent rejects mismatch | TM-ENV-001 |

---

## 2. Additional architecture MUST properties (#3 encodes; does not weaken)

From architecture §3.4–§3.5, §5.6.1, §5.9:

| Topic | Requirement |
| --- | --- |
| V1 Agent binding | Exactly one org + one env at activation |
| Mutating authz binding (§3.5) | Minimum fields: `operation_id`, `actor`, `organization_id`, `environment_id`, `agent_id`, `connection_id`, Action id or approved-field op identity, `expiry`/freshness |
| Agent reject duty | Reject missing/invalid/expired/mismatched bindings and org/env/connection mismatches |
| Non-mutating sensitive commands | Discovery/search/preview still carry org/env/agent/connection + authenticity/integrity; preview SHOULD carry read grant / binding shape as #3 refines |
| Result durability | Agent durably retains/retries results until Cloud ack; correlate by `operation_id`; support pending/succeeded/failed/unknown; no silent terminalization; reconcile on reconnect |
| Duplicate delivery | Safe idempotent handling for same `operation_id` |
| Offline queue | TTL and/or max depth; cancel/invalidate on revoke of Agent/session/permission/connection |
| Enrollment | Short-lived, single-use enrollment proof pre-bound to org+env; audited (see SR-ENROLL-* / TM-ENR-*) |

---

## 3. Explicitly out of scope for Security in this handoff

- Choice of mTLS vs device certificates vs signed tokens vs other
- Wire encoding / framing / session resumption details
- Exact at-least-once vs exactly-once **claim** — provided properties above hold (especially replay + idempotency)

---

## 4. Conformance table template (Architect fills in #3 ADR)

Copy into Issue #3 ADR and complete **Mechanism mapping** + **Evidence**:

| Property ID | Mechanism mapping (Architect) | Agent enforcement point | Cloud enforcement point | Evidence / test plan | Security re-review |
| --- | --- | --- | --- | --- | --- |
| P-OUTBOUND | _TBD_ | _TBD_ | _TBD_ | _TBD_ | PENDING |
| P-ENCRYPT | _TBD_ | _TBD_ | _TBD_ | _TBD_ | PENDING |
| P-AGENT-ID | _TBD_ | _TBD_ | _TBD_ | _TBD_ | PENDING |
| P-CMD-AUTH | _TBD_ | _TBD_ | _TBD_ | _TBD_ | PENDING |
| P-REPLAY | _TBD_ | _TBD_ | _TBD_ | _TBD_ | PENDING |
| P-ORG-BIND | _TBD_ | _TBD_ | _TBD_ | _TBD_ | PENDING |
| P-ENV-BIND | _TBD_ | _TBD_ | _TBD_ | _TBD_ | PENDING |
| §3.5 binding encoding | _TBD_ | Validate all min fields | Mint only after confirm+RBAC | _TBD_ | PENDING |
| §5.6.1 result/ack/reconcile | _TBD_ | Durable retain + retry | Lifecycle + ack | _TBD_ | PENDING |
| Enrollment proof | _TBD_ | Present once | Single-use TTL org+env | _TBD_ | PENDING |
| Offline queue controls | _TBD_ | Reject expired/invalid | TTL/depth/cancel-on-revoke | _TBD_ | PENDING |

**Fail condition:** Any row left without a mechanism that satisfies the property, or any mechanism that weakens architecture §2.4 / §3.4–3.5.

---

## 5. Security re-review commitment

When Issue #3 ADR is proposed, Security will review against:

1. This handoff checklist (all properties PASS/FAIL)
2. [v1-threat-model.md](./v1-threat-model.md) §11 pending list
3. [threat-register.md](./threat-register.md) threats TM-AG-*, TM-ENR-*, TM-ACT-007/008, TM-Q-*
4. ADR 0006

Verdict will be PASS / FAIL / PASS WITH NON-BLOCKING FINDINGS. Mechanism novelty does not waive properties.

---

## Document history

| Date | Change |
| --- | --- |
| 2026-09-19 | Issue #4 handoff from ADR 0006 + formal TM |
