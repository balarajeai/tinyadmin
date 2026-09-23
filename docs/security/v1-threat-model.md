# TinyAdmin V1 Threat Model (Production-MVP)

| Field | Value |
| --- | --- |
| **Status** | Security draft for Architect/QA/Code Review — **not** merge authority; **not** production approval |
| **Issue** | [#4](https://github.com/balarajeai/tinyadmin/issues/4) |
| **Author** | Security Engineer |
| **Mode** | Production-MVP (realistic ship-stoppers only; not an enterprise encyclopedia) |
| **Date** | 2026-09-23 (America/Chicago) |
| **Inputs (established)** | Product lock `docs/product/v1-requirements.md`; architecture `docs/architecture/v1-system-architecture.md` + ADRs 0001–0006 (Issue #2); protocol `docs/architecture/agent-cloud-protocol-v1.md` + ADR 0007 (Issue #3 Security PASS); domain `docs/architecture/domain/v1-domain-model.md` + ADR 0008 (Issue #6 Security PASS WITH NON-BLOCKING) |
| **Supersedes** | Exhaustive multi-file package on PR #14 and prior PR #11 drafts — those remain historical; **this single file is the Issue #4 Production-MVP deliverable** |

This document identifies threats that could realistically cause V1 to expose a customer production database, allow unauthorized mutations, cross org/environment boundaries, compromise an Agent, leak DB credentials, execute unauthorized commands, replay mutations, bypass Safe Action limits, corrupt audit, or perform unsafe rollback.

It does **not** redesign architecture that already passed Issues #2 / #3 / #6 gates. It does **not** fully specify Issue #5 security requirements.

---

## 1. Trust boundaries (in scope)

```
Browser / User
    ↓  B1 (TLS, session, server-side authz)
TinyAdmin Cloud (control plane + Cloud PostgreSQL)
    ↓  B2 (WSS/TLS + signed envelopes; Agent outbound)
TinyAdmin Agent (customer infra)
    ↓  B3 (local DB creds / secret refs)
Customer PostgreSQL / MongoDB
```

Also modeled: invitations/reset tokens, org membership/RBAC, environments, Agent enrollment/keys/revocation, Connection metadata + `customer_secret_ref`, discovery/search, Safe Actions, approved-field edits, preview → confirm → authorize → dispatch → result, retries/reconnects/cancel, audit, rollback.

**Out of scope for this MVP model:** full pentest, Contabo host hardening checklist execution, billing/SSO, AI-generated mutations, redesigning one-Agent-per-Connection topology (see FA-CONN-BLAST).

---

## 2. Established security properties (must preserve)

These are **architecture/protocol/domain guarantees**. Implementation must realize them; this TM does not reopen them unless a CRITICAL/HIGH flaw is found (none found that requires redesign).

1. Customer DB credentials **never** stored in TinyAdmin Cloud.
2. Credentials remain in customer infra; only Agent/local secret mechanism can use them.
3. Customer DBs need no public `5432`/`27017` for TinyAdmin.
4. Agent initiates outbound connectivity.
5. Cloud↔Agent uses TLS (`wss://` / HTTPS).
6. Each Agent has its own authenticated identity (not org-wide shared secret alone).
7. Agent bound to exactly one organization + one environment (immutable).
8. Connections match Agent org/environment binding.
9. Cloud-authorized mutation commands are cryptographically signed.
10. Authorization envelopes bind org, env, Agent, connection, operation, actor, allowed action/effect, expiry (+ payload digest).
11. Replay/duplicate delivery must not cause duplicate mutation.
12. At-least-once delivery allowed; mutation path has idempotency / durable execution-state protections as defined in #3.
13. Unknown outcomes stay `unknown` / reconciliation — never silent success/failure.
14. Safe Actions and approved-field editing never become arbitrary SQL/Mongo.
15. Preview must not mutate customer data.
16. Confirmation occurs before mutation authorization/dispatch.
17. Production and non-production must not silently cross.
18. Client-supplied organization IDs are never authorization.
19. Audit is append-oriented; ordinary app/ops users cannot modify/delete history.
20. Rollback is conditional, separately authorized/confirmed/audited — never unconditional snapshot restore.

---

## 3. Mitigation state legend

| State | Meaning |
| --- | --- |
| **ESTABLISHED** | Guaranteed by merged architecture / protocol / domain decisions. |
| **IMPLEMENTATION REQUIRED** | Architecture supports it; Backend / Agent / Platform must implement and test before Go-live. |
| **DEFERRED** | Not required for V1 MVP; residual risk acceptable without Founder exception **or** explicitly parked with residual note. |

---

## 4. Threat register (MVP)

Severity: **CRITICAL** / **HIGH** / **MEDIUM** / **LOW**.

A **blocking** threat for V1 ship means: cannot safely ship unless mitigated **or** Founder records an explicit risk decision.

### 4.1 Authentication / sessions / invites

#### TM-AUTH-001 — Stolen SaaS session drives production mutations
| | |
| --- | --- |
| **ASSET / BOUNDARY** | User session / B1 |
| **THREAT** | Attacker with stolen browser session executes Safe Actions / field edits in victim org. |
| **ATTACK / FAILURE** | XSS, malware, shared device, session fixation, missing rotation on password change. |
| **IMPACT** | Unauthorized customer-DB mutations via legitimate Cloud path. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: server-side sessions; sessions revocable (domain). |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: httpOnly Secure cookies (or equivalent), CSRF defenses for cookie sessions, logout/password-change session revoke, idle/absolute timeouts, production step-up confirmation (Issue #5). |
| **RESIDUAL RISK** | Endpoint malware with live session still abuses user privileges. |
| **OWNER** | Backend + Issue #5 |

#### TM-AUTH-002 — Invite / reset token abuse
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Invitation / password-reset tokens / B1 |
| **THREAT** | Guessed, leaked, or replayed tokens grant org membership or account takeover. |
| **ATTACK / FAILURE** | Long-lived cleartext tokens in logs/email forwards; missing single-use/TTL. |
| **IMPACT** | Cross-org foothold or account takeover → unauthorized Actions. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: domain requires hashed, expiring, single-use tokens. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: store only hashes; short TTL; single-use; rate-limit issue/consume; never log raw tokens. |
| **RESIDUAL RISK** | Email compromise still succeeds within TTL. |
| **OWNER** | Backend |

#### TM-AUTH-003 — Disabled/revoked user continues to mutate
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Membership / session / B1→Cloud |
| **THREAT** | Revoked member or disabled user retains effective access. |
| **ATTACK / FAILURE** | Authz checks membership only at login; outbox already minted; long-lived session. |
| **IMPACT** | Post-revocation mutation of customer data. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: protocol cancel/revoke for Agent commands; membership status in domain. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: check active membership+permissions on every sensitive API; revoke sessions on membership revoke; cancel undelivered/unexecuted mutating outbox for that actor where feasible. |
| **RESIDUAL RISK** | Narrow race if Agent already committed (honest audit of success). |
| **OWNER** | Backend + Agent |

### 4.2 Authorization / tenant isolation

#### TM-TENANT-001 — Cross-organization access (BOLA/IDOR)
| | |
| --- | --- |
| **ASSET / BOUNDARY** | All tenant-owned resources / B1 |
| **THREAT** | Org A user reads/mutates Org B resources by swapping IDs. |
| **ATTACK / FAILURE** | Client-supplied `organization_id` trusted; missing org predicate on queries. |
| **IMPACT** | Cross-tenant data exposure / mutation — **company-ending**. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: domain invariants — every tenant resource owned by exactly one org; client org id never authorization; architecture tenant isolation. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: server derives org from authenticated membership; enforce `organization_id` on every query/mutation; automated cross-org deny tests. |
| **RESIDUAL RISK** | None acceptable if tests pass; any residual escape is ship-blocking. |
| **OWNER** | Backend + QA |

#### TM-TENANT-002 — Privilege escalation / define-vs-run confusion
| | |
| --- | --- |
| **ASSET / BOUNDARY** | RBAC / Action definitions / B1 |
| **THREAT** | Operator who may only *run* Actions can *define* dangerous Actions (or vice versa), or expand approved fields. |
| **ATTACK / FAILURE** | Single “admin” permission; UI hides but API allows define. |
| **IMPACT** | Policy bypass → arbitrary-looking mutations within Action DSL / field allowlist expansion. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: define vs run permission split required in architecture/domain. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: separate permission keys enforced server-side; Issue #5 names exact keys; tests for define≠run. |
| **RESIDUAL RISK** | Compromised true admin can still define risky Actions (operational). |
| **OWNER** | Backend + Issue #5 |

#### TM-TENANT-003 — Client-controlled environment / resource identifiers
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Environment, Agent, Connection IDs / B1 |
| **THREAT** | Attacker substitutes production `environment_id` / `connection_id` on an otherwise authorized request. |
| **ATTACK / FAILURE** | Authz checks org only; ignores env binding on Action/Connection. |
| **IMPACT** | Staging operator hits production DB. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: env isolation; Operation binds org+env+agent+connection; Agent rejects mismatch. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: resolve resources server-side; verify Action/Connection/Agent env consistency before preview/confirm/dispatch; Agent enforces envelope bindings. |
| **RESIDUAL RISK** | Misconfigured Connection pointing at prod DB labeled staging (customer ops) — see FA-CONN-BLAST guidance. |
| **OWNER** | Backend + Agent |

### 4.3 Environment isolation

#### TM-ENV-001 — Silent production / non-production crossover
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Environment / Agent / Connection / B1–B2 |
| **THREAT** | Production and staging resources mix without explicit, obvious barriers. |
| **ATTACK / FAILURE** | Shared Agent across envs; Connection env mutable; UX treats env as cosmetic label. |
| **IMPACT** | Accidental production mutation. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: Agent/Connection org+env immutable; one Agent ↔ one env; no silent cross. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: DB constraints + API immutability; production visual/operational distinguishability; production confirmation hardening (Issue #5). |
| **RESIDUAL RISK** | Human labels wrong DB host in Connection config. |
| **OWNER** | Backend + Frontend + Issue #5 |

### 4.4 Agent identity / enrollment / keys

#### TM-AGENT-001 — Stolen enrollment token → rogue Agent
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Enrollment / B2 |
| **THREAT** | Attacker redeems enrollment token and binds attacker-controlled Agent. |
| **ATTACK / FAILURE** | Token in chat/logs; long TTL; reusable token. |
| **IMPACT** | Attacker Agent receives commands / may present attacker DB as customer DB; or holds position to exfiltrate command contents / returned rows. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: one-time enrollment; org+env pre-bind; Ed25519 device key at activation (ADR 0007). |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: short TTL; single-use; hash-at-rest; display token once; audit enrollment; optional approver confirm for production enroll (Issue #5). |
| **RESIDUAL RISK** | Attacker with token before consume still wins within TTL. |
| **OWNER** | Backend + Agent + Issue #5 |

#### TM-AGENT-002 — Stolen Agent private key / impersonation
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Agent key material / B2–B3 |
| **THREAT** | Attacker possessing Agent private key authenticates as that Agent. |
| **ATTACK / FAILURE** | Key on disk world-readable; backup leak; malware on Agent host. |
| **IMPACT** | Receive signed mutate commands; access customer DBs configured on that Agent; spoof results if result auth weak. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: per-Agent key; Cloud stores public key only; protected local storage required (ADR 0007). |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: restrict file perms / OS secret store; revoke Agent invalidates sessions + cancels pending mutates; rotate via re-enroll procedure; detect duplicate concurrent sessions (Issue #5 policy). |
| **RESIDUAL RISK** | Full Agent-host compromise = FA-CONN-BLAST radius (all Connections on that Agent). |
| **OWNER** | Agent + Backend + customer ops |

#### TM-AGENT-003 — Revoked Agent reconnects or rebinds
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Agent lifecycle / B2 |
| **THREAT** | Revoked/disabled Agent continues session or changes org/env binding. |
| **ATTACK / FAILURE** | Status not checked on reconnect; binding fields mutable. |
| **IMPACT** | Continued command receipt post-revoke; env escape. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: immutable org/env; revoke terminates sessions; cancel sync before mutate (ADR 0007); domain Agent status. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: reject revoked Agents at session and command validation; immutability enforced in DB; no silent rebind. |
| **RESIDUAL RISK** | Residual commit race documented in protocol. |
| **OWNER** | Backend + Agent |

### 4.5 Cloud→Agent commands / delivery

#### TM-CMD-001 — Forged or modified mutate command
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Signed authorization envelope / B2 |
| **THREAT** | Attacker forges Cloud command or tampers payload/targets. |
| **ATTACK / FAILURE** | Unsigned frames; signature omits mutation payload; Agent skips verify. |
| **IMPACT** | Unauthorized customer-DB mutation. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: Cloud Ed25519 envelopes; signature covers auth fields + `mutation_payload_sha256` (JCS); Agent fail-closed (ADR 0007 / SEC-PR12-001). |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: exact canonicalization + verify-before-execute tests; unknown `kid` reject; no execute without verified envelope. |
| **RESIDUAL RISK** | Compromised Cloud signing key (platform). |
| **OWNER** | Backend + Agent |

#### TM-CMD-002 — Replay / duplicate delivery double-mutates
| | |
| --- | --- |
| **ASSET / BOUNDARY** | operation_id / Agent execution state / B2 |
| **THREAT** | At-least-once delivery or attacker replay causes second mutation. |
| **ATTACK / FAILURE** | No durable execution state; blind retry on timeout. |
| **IMPACT** | Duplicate side effects (charges, status flips, deletes). |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: unique `operation_id`; Agent durable state; duplicate after terminal decision returns prior result; no blind re-exec when indeterminate (ADR 0007). |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: persist execution state crash-safe; tests for duplicate delivery; `unknown` + reconcile when commit indeterminate. |
| **RESIDUAL RISK** | Engine-specific crash windows → `unknown` (honest). |
| **OWNER** | Agent + Backend |

#### TM-CMD-003 — Expired / revoked authorization still executes
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Envelope `exp` / cancel path / B2 |
| **THREAT** | Stale envelope or revoked actor/Agent/Action still mutates. |
| **ATTACK / FAILURE** | Agent skips `exp`; executes local queue after revoke without cancel sync. |
| **IMPACT** | Mutation after intended revoke. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: short-lived mutate TTL; cancel/revoke sync before pending mutate; re-check immediately before execute (ADR 0007). |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: enforce exp+skew; implement cancel sync; fail closed if sync incomplete. |
| **RESIDUAL RISK** | Documented narrow race if commit already started. |
| **OWNER** | Agent + Backend |

#### TM-CMD-004 — Confused deputy / command substitution
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Authorization binding / B1–B2 |
| **THREAT** | Agent executes a command under Cloud authority against wrong connection/Action/targets. |
| **ATTACK / FAILURE** | Binding missing connection_id or Action id; Agent trusts unsigned body fields. |
| **IMPACT** | Wrong-DB or wrong-Action mutation. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: envelope binds org, env, agent, connection, operation, actor, action/effect, expiry, payload digest; Agent rejects mismatch. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: Agent compares local binding to envelope; Connection allowlist; no partial verify. |
| **RESIDUAL RISK** | None if implemented as specified. |
| **OWNER** | Agent + Backend |

### 4.6 Database access / secrets

#### TM-DB-001 — Customer DB credentials reach Cloud / logs / audit
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Secrets / B1–B3 |
| **THREAT** | Password or connection string stored in Cloud, mirrored into audit/errors/support tools. |
| **ATTACK / FAILURE** | `customer_secret_ref` misused as secret store; exception messages include DSN; debug logs. |
| **IMPACT** | Credential theft from Cloud breach or log access. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: Cloud stores no customer DB credentials; `customer_secret_ref` is reference/label only; domain + product lock. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: schema/API forbid secret columns; redaction middleware; Agent never echoes secrets in results; Issue #5 redaction catalog. |
| **RESIDUAL RISK** | Secrets remain on Agent host (customer responsibility + hardening guidance). |
| **OWNER** | Backend + Agent + Issue #5 |

#### TM-DB-002 — Excessive native DB privileges / escaping Safe Actions
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Agent DB role / B3 |
| **THREAT** | Agent DB user can DROP/SELECT anything; bug or injection reaches raw SQL/Mongo. |
| **ATTACK / FAILURE** | Superuser Agent role; string-concat queries; Action DSL compiles to arbitrary statements. |
| **IMPACT** | Broad data compromise / destruction beyond intended Action. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: no arbitrary SQL/Mongo product surface; parameterized driver use expected; Safe Action / approved-field only. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: structured Action effects only; parameterized APIs; customer guidance for least-privilege DB users; deny raw query APIs. |
| **RESIDUAL RISK** | Customer grants Agent overly broad DB role (FA-CONN-BLAST related). |
| **OWNER** | Agent + Backend + customer ops |

### 4.7 Safe Actions / approved-field editing

#### TM-ACTION-001 — Malicious or over-broad Action definition
| | |
| --- | --- |
| **ASSET / BOUNDARY** | ActionDefinition / B1 |
| **THREAT** | Admin defines Action that updates unbounded rows or sensitive columns. |
| **ATTACK / FAILURE** | Missing max-affected bounds; effect DSL too powerful. |
| **IMPACT** | Mass production corruption via “approved” Action. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: Actions are constrained definitions; envelope carries `max_affected_records`; not a generic DB editor. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: server-side max-record enforcement; effect allowlist; define permission gated; production extra confirm (Issue #5). |
| **RESIDUAL RISK** | Powerful-but-allowed Actions remain a trust-in-admin residual. |
| **OWNER** | Backend + Issue #5 |

#### TM-ACTION-002 — Parameter manipulation / max-record bypass
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Execute path / B1–B2 |
| **THREAT** | Attacker alters Action parameters or raises limits between UI and Agent. |
| **ATTACK / FAILURE** | Limits only enforced in UI; payload not covered by signature. |
| **IMPACT** | Broader mutation than authorized. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: mutation payload digest inside signed envelope; Agent verifies digest + max_affected_records. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: Cloud computes digest from authorized params; Agent enforces max; reject on mismatch. |
| **RESIDUAL RISK** | None if verify path is mandatory. |
| **OWNER** | Backend + Agent |

#### TM-ACTION-003 — Definition changed between preview and execute (TOCTOU)
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Preview → confirm → execute / B1 |
| **THREAT** | Action/field config changes after preview; execute uses new definition. |
| **ATTACK / FAILURE** | Execute loads current definition instead of pinned preview version. |
| **IMPACT** | User confirms A, system executes B. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: confirmation before authorize/dispatch; domain Operation binding; protocol payload binding. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: pin Action/field config version (or content hash) into Operation/envelope; reject if definition changed; Issue #5. |
| **RESIDUAL RISK** | Underlying row data may still change (honest preview limitation). |
| **OWNER** | Backend + Issue #5 |

#### TM-ACTION-004 — Unauthorized Action / field-edit execution
| | |
| --- | --- |
| **ASSET / BOUNDARY** | RBAC / B1 |
| **THREAT** | User without `action.run` / `field_edit.run` executes mutate. |
| **ATTACK / FAILURE** | Authz only on define APIs; execute omits check. |
| **IMPACT** | Unauthorized mutation. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: define≠run; Operation records actor; server-side authz required. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: check run permission at confirm and before minting envelope; Actor recorded immutably. |
| **RESIDUAL RISK** | None material if enforced. |
| **OWNER** | Backend |

### 4.8 Preview / confirmation

#### TM-PREVIEW-001 — Preview mutates customer data
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Preview command / B2–B3 |
| **THREAT** | Preview path writes to customer DB. |
| **ATTACK / FAILURE** | Shared execute codepath; “dry-run” still commits; Mongo write concern misuse. |
| **IMPACT** | Unexpected production change without confirm. |
| **SEVERITY** | **CRITICAL** |
| **EXISTING CONTROL** | ESTABLISHED: preview must not mutate (product + domain + SEC-DM-001 intent); confirm before mutate authorize. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: separate read-only Agent handlers; no write APIs on preview; automated tests that preview leaves DB unchanged; Issue #5 SR-PREVIEW. |
| **RESIDUAL RISK** | None acceptable. |
| **OWNER** | Agent + Backend + QA |

#### TM-PREVIEW-002 — Confirmation bound to wrong / stale operation
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Confirmation / B1 |
| **THREAT** | User confirms operation A; Cloud dispatches operation B (or stale preview). |
| **ATTACK / FAILURE** | Confirm token not bound to operation_id + payload hash; UI race. |
| **IMPACT** | Wrong mutation authorized. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: Operation binds org/env/agent/connection/actor/action; confirm before mint. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: confirm token/record binds `operation_id` + preview fingerprint; single-use confirm; reject mismatch. |
| **RESIDUAL RISK** | Data drift after preview (disclose limitation). |
| **OWNER** | Backend |

#### TM-PREVIEW-003 — Production confirmation bypass
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Production UX / B1 |
| **THREAT** | Production mutates without required confirmation / step-up. |
| **ATTACK / FAILURE** | Client skips confirm API; server mints envelope without confirm row. |
| **IMPACT** | Accidental or scripted production damage. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: confirm-before-dispatch invariant in domain. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: server refuses mutate mint without valid Confirmation; production flags (Issue #5). |
| **RESIDUAL RISK** | Authorized user still confirms harmful Action deliberately. |
| **OWNER** | Backend + Issue #5 |

### 4.9 Execution / results / reconciliation

#### TM-EXEC-001 — Lost / spoofed / tampered results → wrong terminal state
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Result path / B2 |
| **THREAT** | Attacker or bug marks success/failure incorrectly; spoofed Agent result. |
| **ATTACK / FAILURE** | Unauthenticated result POST; Cloud coerces timeout to failed/succeeded. |
| **IMPACT** | Operators retry wrongly; audit lies; duplicate or abandoned mutations. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: durable results; authenticated result_ack; `pending|succeeded|failed|unknown`; no silent terminalization (#3 + domain). |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: authenticate Agent results; timeouts → `unknown`/reconcile; never invent success. |
| **RESIDUAL RISK** | Manual reconciliation effort under `unknown`. |
| **OWNER** | Backend + Agent |

### 4.10 Audit

#### TM-AUDIT-001 — Audit deletion / modification / gap
| | |
| --- | --- |
| **ASSET / BOUNDARY** | AuditEvent / Cloud DB |
| **THREAT** | Insider or buggy API deletes/edits audit or fails to write mutate attempts. |
| **ATTACK / FAILURE** | UPDATE/DELETE granted to app role; best-effort audit after mutate. |
| **IMPACT** | Non-repudiation failure; cover-ups. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: append-oriented audit; ops users cannot modify/delete; ≥1 year retention target. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: DB privileges deny update/delete for app roles; write audit for every mutate attempt (including rejects where required); Issue #5 WORM/ops procedure. |
| **RESIDUAL RISK** | Cloud DB superuser can still alter storage (platform residual). |
| **OWNER** | Backend + Platform + Issue #5 |

#### TM-AUDIT-002 — Secrets / sensitive values in audit
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Audit payloads / B1 |
| **THREAT** | Before/after images include secrets, passwords, tokens, full PANs, etc. |
| **ATTACK / FAILURE** | Dump entire row to audit without redaction. |
| **IMPACT** | Audit store becomes high-value secret cache. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: domain flags secret redaction DEPENDS-#5. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: Issue #5 redaction catalog; default deny high-sensitivity fields in audit bodies. |
| **RESIDUAL RISK** | Some business PII still in audit (necessary for support — minimize). |
| **OWNER** | Issue #5 + Backend |

### 4.11 Rollback

#### TM-ROLLBACK-001 — Unsafe / unauthorized / cross-tenant rollback
| | |
| --- | --- |
| **ASSET / BOUNDARY** | Rollback Operation / B1–B3 |
| **THREAT** | Rollback offered when unsafe; wrong operation; wrong tenant/env; no separate authz. |
| **ATTACK / FAILURE** | Treat rollback as unconditional restore; reuse original authz; skip confirm. |
| **IMPACT** | Additional corruption; privilege bypass; cross-env damage. |
| **SEVERITY** | **HIGH** |
| **EXISTING CONTROL** | ESTABLISHED: conditional rollback only; separately authorized, confirmed, audited; linked to parent Operation; never unconditional snapshot restore. |
| **REQUIRED V1 MITIGATION** | IMPLEMENTATION REQUIRED: eligibility checks (state unchanged / declared reversible); new Operation + envelope; same tenant/env/connection binding; Issue #5 SR-ROLLBACK. |
| **RESIDUAL RISK** | Concurrent legitimate writes may still make rollback unsafe — refuse rather than guess. |
| **OWNER** | Backend + Agent + Issue #5 |

---

## 5. FA-CONN-BLAST (one Agent, multiple Connections)

### Decision for V1 MVP

**Do not redesign** V1 into one Agent process per database.

**Accepted default (Founder product lock + architecture):** one Agent per organization **environment**/network may manage **multiple** explicitly configured Connections.

### Blast radius if Agent is compromised

Attacker with Agent host + private key + local secret access can:

- read/use **all** DB credentials configured for that Agent’s Connections;
- receive Cloud-signed commands for those Connections (still constrained by envelopes, but attacker can also talk to DBs directly with stolen creds);
- spoof/suppress Agent-side results until detected.

This is **HIGH residual concentration risk**, not silently zero.

### MVP compensating controls (required)

| ID | Control | State |
| --- | --- | --- |
| CC-1 | Dedicated **least-privilege** DB users per Connection (no superuser) | IMPLEMENTATION REQUIRED (customer runbook + Agent docs) |
| CC-2 | Explicit Connection configuration; no implicit “scan all DSN files” | ESTABLISHED intent → IMPLEMENTATION REQUIRED |
| CC-3 | Secret isolation per Connection where practical (separate secret refs) | IMPLEMENTATION REQUIRED |
| CC-4 | Agent-side Connection **allowlist** matching Cloud Connection IDs | ESTABLISHED binding → IMPLEMENTATION REQUIRED |
| CC-5 | Immutable org/environment Agent binding | ESTABLISHED |
| CC-6 | Command→Connection binding in signed envelope; Agent reject mismatch | ESTABLISHED → IMPLEMENTATION REQUIRED |
| CC-7 | Operational guidance: customers needing stronger isolation run **separate Agents** (e.g. prod vs high-value DB) | DEFERRED as product default change; **guidance REQUIRED** in V1 docs |

### Residual risk statement

**RESIDUAL (HIGH):** Compromise of a multi-Connection Agent host yields credential and data-plane access to every DB that Agent can reach, **bypassing** Cloud Safe Action policy for direct DB access.

**Founder decision:** **Not required to block V1** if CC-1..CC-7 are implemented/documented. Escalate to Founder for ACCEPT/REJECT/DEFER only if product later mandates single-Connection Agents or if a customer segment cannot accept this residual.

**Security position:** Accept residual **with compensating controls**; do **not** silently treat blast radius as zero.

---

## 6. Issue #5 handoff (requirements to formalize — not fully specified here)

Issue #5 must turn these into enforceable SHALL requirements (exact keys, tests, UX rules):

| ID | Theme | From threats |
| --- | --- | --- |
| **SR-TENANT** | Server-derived org; deny client org-as-authz; cross-org automated tests | TM-TENANT-001 |
| **SR-AUTHZ** | Exact define vs run permission keys; membership checks on every sensitive call | TM-TENANT-002, TM-ACTION-004, TM-AUTH-003 |
| **SR-ENV** | Env consistency checks; production distinguishability + step-up | TM-ENV-001, TM-TENANT-003, TM-PREVIEW-003 |
| **SR-AGENT** | Enrollment TTL/single-use; key storage; revoke; duplicate-session policy | TM-AGENT-001..003 |
| **SR-CMD** | Envelope verify mandatory; cancel/exp; no blind retry | TM-CMD-001..004 |
| **SR-ACTION** | Effect allowlist; max records; config version pin preview→execute | TM-ACTION-001..003 |
| **SR-PREVIEW** | Preview non-mutation proof; confirm binding fingerprint | TM-PREVIEW-001..002 |
| **SR-EXEC** | Authenticated results; `unknown`/reconcile rules | TM-EXEC-001 |
| **SR-AUDIT** | Append-only enforcement; mutate-attempt coverage; retention | TM-AUDIT-001 |
| **SR-REDACT** | Audit/log redaction catalog | TM-AUDIT-002, TM-DB-001 |
| **SR-ROLLBACK** | Eligibility; separate authz/confirm/audit; parent link | TM-ROLLBACK-001 |
| **SR-SECRETS** | No Cloud DB secrets; `customer_secret_ref` semantics; log redaction | TM-DB-001 |
| **SR-CONN-BLAST** | CC-1..CC-7 customer + Agent guidance | FA-CONN-BLAST |

Do **not** start Issue #5 from this PR alone; this table is the handoff index.

---

## 7. Consistency with Issues #2 / #3 / #6

| Gate | Consistency check |
| --- | --- |
| **#2 architecture** | No property weakened; TM consumes ADR 0006 P-* and architecture trust boundaries. |
| **#3 protocol** | Treats signed envelopes, revoke/cancel, at-least-once + durable idempotency, `unknown` as **ESTABLISHED**; lists only implementation duties. |
| **#6 domain** | Aligns with org ownership, Agent/Connection env bind, confirm-before-mutate, preview non-mutate, conditional rollback, append-only audit, lifecycle statuses. Non-blocking SEC-DM-001/002 become Issue #5 / implementation clarity, not architecture reopen. |

**No CRITICAL/HIGH flaw found that requires redesigning approved architecture.**

---

## 8. Ship-blocking summary

| Severity | Count | Ship rule |
| --- | --- | --- |
| CRITICAL | 11 | Must be mitigated in implementation (controls above) before production V1 |
| HIGH | 16 | Must be mitigated or explicitly Founder-accepted |
| MEDIUM | 0 | — |
| LOW | 0 | — |
| **Total MVP threats** | **27** | Plus FA-CONN-BLAST residual with CC-1..CC-7 |

**Founder decisions required now:** none mandatory for topology. FA-CONN-BLAST residual accepted **with** compensating controls; Founder may later ACCEPT/REJECT/DEFER a stricter one-Agent-per-Connection policy.

**Blockers to closing Issue #4:** Architect feasibility review, QA testability skim, Independent Code Review of this doc PR — **not** implementation start.

---

## 9. Document history

| Date | Change |
| --- | --- |
| 2026-09-23 | Production-MVP rewrite: single artifact; aligned to merged #2/#3/#6; supersedes exhaustive PR #14 package for Issue #4 deliverable |