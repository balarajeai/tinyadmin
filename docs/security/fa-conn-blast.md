# FA-CONN-BLAST — Multi-connection Agent blast-radius disposition

**Status:** Formal Security disposition (Issue #4) — **NOT silently accepted**  
**Placeholder ID:** `FA-CONN-BLAST`  
**Related threats:** `TM-CONN-002`, `TM-HST-001`, architecture §7 (SEC-PR10-005)  
**Date:** 2026-09-19 (America/Chicago)  
**Owner:** Security Engineer (FA text); Founder (acceptance decision)

---

## 1. Context

Founder V1 product lock (§11) defaults to: **one TinyAdmin Agent per customer environment/network may manage multiple explicitly configured database connections.**

Architecture (MERGED §7) retains this topology and lists compensating controls plus a Founder residual acceptance placeholder. Security owns the formal FA text here. This document does **not** invent acceptance.

---

## 2. Realistic blast-radius scenarios

| # | Scenario | What an attacker gains |
| --- | --- | --- |
| S1 | Agent host compromise (malware, stolen SSH, malicious insider on customer jump box) | Read **all** local DB credentials configured for that Agent; connect directly to each customer DB with those roles |
| S2 | Memory/disk extraction of Agent secret store | Same as S1 without full OS control if secrets weakly protected |
| S3 | Stolen long-lived Agent Cloud credential + host access | Receive Cloud commands for the org/env **and** use local DB credentials; spoof or suppress results until revoke |
| S4 | Single over-privileged DB role reused across connections | Compromise of one connection credential yields DDL/destructive power on multiple databases |
| S5 | Delayed detection / revoke | Queued mutating commands may still be valuable to attacker until enrollment revoke + binding invalidation |

Blast radius is **credential concentration + mutate capability** across every connection attached to that Agent — not cross-org (V1 Agent is one org + one env per §3.4).

---

## 3. Required compensating controls (must-haves)

These are **required** whether or not Founder later accepts residual risk. Acceptance without these controls is **invalid**.

| ID | Control | Owner |
| --- | --- | --- |
| CC-1 | **Per-connection least-privilege DB credentials** where the customer DB allows (no shared SUPERUSER/root across connections; no DDL unless required) | Customer ops (guided) + Agent docs (`SR-DB-LEAST`) |
| CC-2 | **Immediate Agent enrollment revocation** that stops further command receipt | Backend / Agent (`SR-AGENT-AUTH`) |
| CC-3 | **Invalidate all queued / outstanding authorization bindings** for that Agent (and cancel-on-revoke per §5.9) | Backend (`SR-QUEUE-*`, §3.5 expiry) |
| CC-4 | **Incident runbook hook:** “compromise one Agent host” — rotate every DB credential that Agent held; re-enroll Agent; audit review for operation_ids in window | Platform / customer runbooks (`SR-CONN-BLAST`) |
| CC-5 | **Connection_id binding enforcement** so compromise of logic cannot silently retarget commands across connections without matching local config | Agent (`SR-CONN-BIND`, §3.4 #5) |
| CC-6 | **No customer DB credentials in Cloud** (limits Cloud compromise from becoming multi-DB credential theft via TinyAdmin) | Backend (Founder hard rule) |
| CC-7 | **Secrets not in Agent/Cloud logs** | Agent + Backend (`SR-LOG-NOSECRET`) |

Optional hardenings (recommended, not substitutes for CC-1..7): OS secret manager / sealed files; separate Agents per high-value production DB; network egress allowlists from Agent host to DB hosts only.

---

## 4. Does residual risk require Founder acceptance?

**Yes.**

After CC-1..7 are designed into V1, a **HIGH** residual remains: one compromised Agent host still concentrates multiple connection credentials and mutate capability for that org/env. Architecture and product default retain multi-connection topology for V1 ops simplicity.

- Silent acceptance by agents/implementers is **forbidden**.
- Founder may **accept**, **reject** (force one-Agent-one-connection for prod), or **defer ship** of multi-connection until stronger isolation.

### Recommended FA text (for Founder to complete — do not invent signature)

```text
FA-CONN-BLAST
Decision: ACCEPT / REJECT / DEFER  (circle one)
Accepting human (Founder): __________________
Date (America/Chicago): __________________
Scope: V1 default — one Agent per org+env may hold multiple explicitly configured DB connections.
Preconditions for ACCEPT (all required):
  - Compensating controls CC-1..CC-7 implemented or scheduled with Security gate SG-12 evidence
  - Customer-facing docs disclose multi-connection blast radius
  - Incident runbook for Agent host compromise exists
Residual risk acknowledged: HIGH — host compromise exposes all credentials configured on that Agent.
Follow-up / expiry: __________________ (e.g. revisit before enterprise tier / multi-env Agents)
If REJECT: V1 production topology becomes one Agent process per connection (or equivalent isolation ADR).
```

---

## 5. Implementation / QA evidence eventually required

| Evidence | Who |
| --- | --- |
| Agent rejects mismatched `connection_id` (automated) | Agent + QA |
| Revoke Agent → no further mutating command execution; queued bindings non-executable | Backend + QA |
| Docs: least-privilege credential guidance + blast-radius disclosure | Agent/Platform docs |
| Runbook: rotate all connection secrets after Agent compromise | Platform |
| Log redaction sample for connection URIs/passwords | QA + Security |
| Recorded FA-CONN-BLAST decision (ACCEPT/REJECT/DEFER) before multi-conn production enablement | Founder |

---

## 6. One-line disposition

**FA-CONN-BLAST: HIGH residual multi-connection credential concentration requires compensating controls CC-1..CC-7 and explicit Founder ACCEPT/REJECT/DEFER — not silently accepted.**

---

## Document history

| Date | Change |
| --- | --- |
| 2026-09-19 | Formal Issue #4 disposition from architecture §7 placeholder + TM-CONN-002 |
