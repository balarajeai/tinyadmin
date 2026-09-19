> **Issue #5 companion (still draft).** Copied from PR #11 for continuity with Issue #4 threat model.
> Primary deliverable of this package is **Issue #4** (threat model + register + FA disposition + handoffs).
> Architect enforceability review and QA testability gates for these SHALL requirements remain **open** under Issue #5.
> Do not treat this copy as Issue #5 complete.

# TinyAdmin V1 Security Requirements

**Status:** Draft for Architect enforceability review + QA testability review  
**Issue:** [#5](https://github.com/balarajeai/tinyadmin/issues/5)  
**Author role:** Security Engineer  
**Date:** 2026-09-18 (America/Chicago)  
**Companion:** [v1-threat-model.md](./v1-threat-model.md)  
**Product lock:** `docs/product/v1-requirements.md`  
**Architecture input:** PR #10 `docs/architecture/v1-system-architecture.md` (does not waive these SHALL statements)

Requirements use **SHALL** / **SHALL NOT** (mandatory), **SHOULD** (strong default), **MAY** (optional). Each requirement is intended to be **testable** by QA or Security. IDs are stable for traceability.

**Issue #3:** This document states **security properties**. Architect selects the concrete protocol (mTLS, signed device JWT, or equivalent) in Issue #3 among options that satisfy the properties. Security does **not** mandate a single protocol mechanism here.

---

## A. Authentication (human users)

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-AUTH-01 | The system SHALL authenticate users with email/password for V1 before granting access to non-public resources. | Unauthenticated calls to protected APIs return 401. |
| SR-AUTH-02 | The system SHALL store password verifiers using a modern memory-hard or salted adaptive hash (e.g. Argon2id / bcrypt / scrypt); plaintext passwords SHALL NOT be stored. | Code review + unit test that verifier is not reversible plaintext. |
| SR-AUTH-03 | The system SHALL enforce a minimum password policy of at least 12 characters (or equivalent entropy policy approved by Security). | Reject short passwords in API tests. |
| SR-AUTH-04 | Login endpoints SHALL apply rate limiting / lockout sufficient to mitigate credential stuffing (exact thresholds MAY be tuned; defaults MUST exist). | Burst login attempts receive 429 or temporary lock. |
| SR-AUTH-05 | Authentication failures SHALL NOT reveal whether the email is registered beyond what product explicitly accepts; responses SHOULD be uniform. | Enumerate emails; compare responses/timing within reasonable bounds. |
| SR-AUTH-CSRF | Cookie-based session authenticating browser APIs SHALL be protected against CSRF (SameSite + anti-CSRF token or equivalent). | Cross-site forged POST without token fails. |

---

## B. Sessions

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-SESS-01 | Sessions SHALL be server-tracked (or equivalent revocable server-side state). Opaque session identifiers SHALL be high-entropy and transmitted securely (Secure + HttpOnly cookies if cookies used). | Cookie flags; session id not in localStorage if cookie model. |
| SR-SESS-02 | The system SHALL invalidate sessions on logout and SHALL support administrative/user revocation of sessions. | Logout then reuse cookie → 401. |
| SR-SESS-03 | The system SHALL rotate session identifier after successful login and after password change. | Pre/post login session id differ. |
| SR-SESS-04 | Sessions SHALL expire after an idle and/or absolute timeout (defaults REQUIRED before GA). | Time-travel/config test or documented TTL with enforcement test. |
| SR-SESS-05 | Session payloads and logs SHALL NOT contain password verifiers or raw session secrets. | Log fixture scan. |

---

## C. Invitations

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-INV-01 | Organization invitations SHALL use a high-entropy, single-use token with a short TTL. | Reuse after accept fails; expired token fails. |
| SR-INV-02 | Accepting an invitation SHALL require authentication as the invited email (or bind the account to that email before membership is granted). | Different account cannot accept. |
| SR-INV-03 | Invitation create/revoke SHALL require appropriate RBAC within the organization and SHALL be audited. | Unauthorized invite → 403; audit row exists. |
| SR-INV-04 | Invitation tokens SHALL NOT appear in Cloud structured application logs. | Log scan on invite creation path. |

---

## D. Password reset

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-RESET-01 | Password reset SHALL use a high-entropy, single-use token with short TTL, bound to the account. | Expired/reused token fails. |
| SR-RESET-02 | Successful password reset SHALL invalidate existing sessions for that user. | Old session rejected after reset. |
| SR-RESET-03 | Reset tokens SHALL NOT be logged in plaintext. | Log scan. |
| SR-RESET-04 | Reset request endpoint SHALL be rate-limited. | Burst → 429. |

---

## E. Authorization / RBAC

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-RBAC-01 | Authorization for sensitive operations SHALL be enforced server-side on every request; UI hiding SHALL NOT be the sole control. | Direct API call without permission → 403. |
| SR-RBAC-02 | The permission model SHALL distinguish at least: (a) manage users/invites, (b) manage Agents/connections, (c) define/configure Actions and approved-field edits, (d) execute Actions / field edits, (e) read audit, (f) request rollback — exact role names MAY vary. | Matrix tests per permission. |
| SR-RBAC-03 | Permission to **define** Actions or approved-field configurations SHALL NOT by itself imply permission to **execute** them in production unless an explicit role grants both. | Define-only role cannot execute. |
| SR-RBAC-04 | Denied authorization attempts on sensitive mutations SHOULD be audited. | 403 path creates audit or security event. |

---

## F. Tenant isolation

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-TEN-01 | Every tenant-owned resource access SHALL be constrained by a trusted organization context derived from server-side membership, not from client assertion alone. | Cross-tenant IDOR tests for connections, Agents, Actions, audit, records orchestration ids. |
| SR-TEN-02 | The system SHALL NOT treat a client-supplied organization identifier as proof of authorization. | Supply victim org id → deny. |
| SR-TEN-03 | Cross-tenant data access SHALL be treated as a CRITICAL defect; automated tests SHALL cover deny paths for primary resource types. | CI tenant isolation suite required for epics touching tenancy. |
| SR-TEN-04 | When a user belongs to multiple organizations, each request SHALL operate in exactly one active organization context that the user is a member of; context switch SHALL be explicit and server-validated. | Sticky wrong-org tests. |
| SR-TEN-05 | Background jobs and command queue workers SHALL apply the same tenant constraints as interactive APIs. | Queue worker cannot drain other org commands. |

---

## G. Environment isolation

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-ENV-01 | Connections, Agents, Action execution context, and command tickets SHALL be bound to a specific environment. | Prod Action cannot target staging connection. |
| SR-ENV-02 | In V1, an Agent SHALL be bound to exactly one organization and exactly one environment at activation; the system SHALL reject enrollment that would span environments. | Attempt dual-env bind fails. *(Architect may propose alternative hard isolation with Security review; silent label-only isolation is forbidden.)* |
| SR-ENV-03 | Connection environment binding SHALL be immutable after creation (or require controlled recreation + audit); Actions SHALL re-resolve env at execute time. | Retarget attempt fails or audits + blocks stale execute. |
| SR-ENV-04 | Production operations SHALL be visually and operationally distinguishable in UI; API error messages SHALL NOT blur env identity. | UI/API contract tests. |

---

## H. Agent enrollment

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-ENROLL-01 | Enrollment credentials SHALL be high-entropy, short-lived, and single-use. | Second use fails. |
| SR-ENROLL-02 | Enrollment SHALL pre-bind intended organization and environment before Agent activation completes. | Activated Agent records match intent. |
| SR-ENROLL-03 | Enrollment creation and successful activation SHALL be audited (without secret material). | Audit events exist; no token in audit. |
| SR-ENROLL-04 | Only authorized org roles SHALL create enrollment intents. | Unauthorized → 403. |

---

## I. Agent authentication & identity

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-AGENT-AUTH-01 | After enrollment, the Agent SHALL authenticate to Cloud with a long-lived credential or key material that Cloud can revoke. | Revoke → Agent session rejected. |
| SR-AGENT-AUTH-02 | Agent identity SHALL map to exactly one `agent_id` + org + environment in Cloud registry for V1. | Registry invariant test. |
| SR-AGENT-AUTH-03 | Cloud SHALL reject Agent connections that present credentials for a revoked or deleted Agent. | Post-revoke connect fails. |
| SR-AGENT-BIND | Org and environment binding for an activated Agent SHALL be immutable; re-assignment SHALL require new enrollment (or a Security-reviewed controlled procedure). | Rebind API denied. |

---

## J. Agent credential rotation / revocation

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-AGENT-ROT-01 | The system SHALL support Agent credential rotation without storing customer DB secrets in Cloud. | Rotate Agent auth material; DB secrets untouched in Cloud DB. |
| SR-AGENT-ROT-02 | The system SHALL support immediate revocation of Agent credentials; pending mutating commands for that Agent SHALL be cancelled or rejected on drain. | Revoke mid-queue; mutate commands do not execute. |
| SR-AGENT-ROT-03 | Rotation and revocation events SHALL be audited. | Audit rows present. |

---

## K. Cloud ↔ Agent transport (properties for Issue #3)

Architect SHALL choose a concrete design in Issue #3 that satisfies **all** of the following. Acceptable option classes include (non-exhaustive): mutual TLS with per-Agent certificates; TLS + Agent-held asymmetric key signing command envelopes; enrollment-bound device keys with signed constrained tokens. **No single option is mandated here.**

| ID | Requirement (property) | QA / design verification sketch |
| --- | --- | --- |
| SR-XPORT-OUTBOUND | Agent SHALL initiate outbound connectivity to Cloud; Cloud SHALL NOT require inbound customer DB ports or inbound Agent admin ports for V1 operation. | Architecture + integration: no Cloud→customer:5432/27017. |
| SR-XPORT-TLS | All Agent↔Cloud application data SHALL be protected by encrypted transport equivalent to modern TLS. | Protocol ADR + integration evidence. |
| SR-XPORT-MUTAUTH | Cloud SHALL authenticate the Agent; Agent SHALL authenticate Cloud (or equivalent mutually authenticated channel / signed channel binding). | Rogue server / rogue Agent tests in lab. |
| SR-CMD-AUTH | Mutating and sensitive commands SHALL be authenticatable as originating from Cloud authorization for that Agent (integrity + authenticity). | Tampered command rejected. |
| SR-CMD-TICKET | Each execute/rollback (and SHOULD each preview/search) SHALL carry a Cloud-issued operation authorization bound to: actor (or acting principal id), org, environment, agent_id, connection_id, action/operation type, expiry, and unique operation id. Agent SHALL reject commands failing binding checks. | Cross-binding mutation attempts fail at Agent. |
| SR-REPLAY | The design SHALL provide replay resistance for mutating commands (nonce/operation id + replay window or equivalent). | Replay same payload → rejected or idempotent no-op per SR-IDEM. |

---

## L. Command authenticity, replay, idempotency

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-IDEM-01 | Mutating operations SHALL be idempotent with respect to a Cloud-issued `operation_id` (or equivalent idempotency key) such that duplicate delivery does not apply the mutation twice. | Deliver execute twice → one DB effect. |
| SR-IDEM-02 | Agent SHALL persist enough dedupe state to honor SR-IDEM-01 across process restart within a defined retention window. | Restart Agent; replay → no second mutate. |
| SR-IDEM-03 | Cloud SHALL NOT mark a mutation successful without correlating Agent result to the same `operation_id`. | Mismatched correlation rejected. |

---

## M. Safe Actions

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-ACT-01 | Mutations via TinyAdmin SHALL be expressible only as Safe Actions or configured approved-field edits; the system SHALL NOT provide arbitrary SQL or arbitrary Mongo mutation interfaces. | Attempt raw SQL API → rejected. |
| SR-ACT-02 | Action definitions SHALL be organization-scoped and environment-applicable per configuration rules; execution SHALL enforce org+env+RBAC. | Cross-tenant Action execute denied. |
| SR-ACT-03 | Action parameters SHALL be validated against an allowlisted schema; unexpected fields SHALL be rejected. | Extra params → 400. |
| SR-ACT-04 | Agent execution of Actions SHALL use parameterized/driver-safe APIs; string-concatenated queries from user input SHALL be treated as defects. | Code review gate SG-5/SG-7. |

---

## N. Approved-field edits

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-FIELD-01 | Approved-field editing SHALL only allow fields explicitly configured for that org/env/connection/resource type. | Edit non-approved field → deny. |
| SR-FIELD-02 | Users SHALL NOT be able to select arbitrary schema fields for mutation merely because discovery listed them. | Discovery list ≠ editable set. |
| SR-FIELD-03 | Configuration of approved fields SHALL require elevated RBAC (define permission) and SHALL be audited. | Unauthorized config → 403; audit exists. |
| SR-FIELD-04 | Approved-field edits SHALL use the same preview/confirm/execute/audit controls as Actions where technically applicable, and SHALL never bypass authz. | Field edit without permission fails; audit on mutate. |

---

## O. Preview

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-PREV-READONLY | Preview SHALL NOT mutate the customer database. | DB write probe / read-only transaction / Agent mode test. |
| SR-PREV-AUTHZ | Preview SHALL require the same org/env/Action authorization as a prerequisite for viewing sensitive preview data (or a documented lesser read permission that is still tenant-scoped). | Unauthorized preview → 403. |
| SR-PREV-HONEST | When preview cannot guarantee final execute results, the API/UI SHALL set an explicit non-authoritative/limitation flag; clients SHALL NOT present false certainty. | Limitation flag contract test. |
| SR-PREV-TOCTOU | Execute path SHALL re-validate critical preconditions at execution time (Agent re-read or equivalent) OR bind execute to a short-lived preview snapshot token with explicit invalidation; product SHALL NOT silently ignore TOCTOU. | Change row between preview and execute → safe fail or disclosed path. |
| SR-PREV-AUDIT | Preview of sensitive Actions SHOULD create an audit or security event (recommended for production env). | Event present for prod preview. |

---

## P. Confirmation

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-CONF-01 | Mutating Actions and approved-field edits SHALL require an explicit confirmation step distinct from preview. | Execute without confirm token → reject. |
| SR-CONF-02 | Production-environment mutations SHALL require an additional confirmation affordance (e.g. typed env acknowledgment or re-auth step-up). Exact UX MAY vary; a distinguishable production gate SHALL exist. | Staging vs prod confirm contract differs. |
| SR-CONF-03 | Confirmation tokens/intents SHALL be bound to the operation parameters and SHALL expire quickly. | Tampered or expired confirm fails. |

---

## Q. Execution

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-EXEC-01 | Execution SHALL only proceed after SR-RBAC, SR-TEN, SR-ENV, SR-CONF, and SR-CMD-TICKET checks succeed. | Negative tests per layer. |
| SR-EXEC-02 | Partial multi-step Action failure SHALL be represented accurately in results and audit (no silent partial success). | Simulated mid-failure → audit status partial/failed. |
| SR-EXEC-03 | Cloud and Agent SHALL refuse Action types not on the allowlisted Action catalog for that tenant. | Unknown action type rejected. |

---

## R. Rollback

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-RB-01 | Rollback SHALL be available only when the Action definition declares reversibility and Agent safety checks pass. | Non-reversible Action → rollback API unavailable. |
| SR-RB-02 | Rollback SHALL require authorization distinct from or equal to execute per RBAC policy, and SHALL always be audited and linked to the original `operation_id`. | Unauthorized rollback denied; audit link present. |
| SR-RB-03 | If relevant state changed so reversal is unsafe, Agent SHALL abort rollback and report unsafe; UI SHALL NOT show success. | Mutate after execute → rollback aborts. |
| SR-RB-04 | The system SHALL NOT advertise universal rollback. | Product copy / API capabilities omit universal claim. |

---

## S. Audit

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-AUDIT-MUT | Every mutation **attempt** (success, failure, partial, rejected after authz intent) SHALL append an audit record with: actor, organization, environment, Action or field-edit identifier, target, timestamp, operation id, result/status, Agent correlation as applicable, before/after where appropriate, rollback link when applicable. | Execute success+fail fixtures create rows. |
| SR-AUDIT-IMMUT | Normal operational users and standard application DB roles used by Cloud SHALL NOT be able to UPDATE or DELETE audit history via product APIs; storage design SHALL enforce append-oriented semantics (e.g. revoke UPDATE/DELETE, trigger, or separate immutable store). | API delete/update audit → fail; DB role test. |
| SR-AUDIT-RET | Retention design target SHALL be ≥ 1 year for V1 audit records. | Config/migration evidence; no auto-purge < 1 year. |
| SR-AUDIT-READ | Audit read/export SHALL enforce tenant isolation and RBAC; exports are Security-review sensitive. | Cross-tenant audit read denied. |
| SR-AUDIT-REDACT | Audit payloads SHALL NOT store customer DB passwords, Agent enrollment secrets, or session tokens; before/after SHOULD redact known secret fields. | Fixture contains password → redacted. |

---

## T. Secrets

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-SECRET-CLOUD-DENY | TinyAdmin Cloud SHALL NOT store, cache, or log customer database passwords or credential URIs containing secrets. | Schema review + negative tests; grep migrations. |
| SR-SECRET-AGENT | Customer DB credentials SHALL remain in customer-controlled infrastructure accessible to the Agent via local config or local secret mechanism. | Architecture conformance test. |
| SR-SECRET-SUPPORT | TinyAdmin support/ops workflows SHALL NOT display or transmit customer DB credentials through the product. | UI/API absence test. |
| SR-SECRET-ATREST | Cloud-held secrets that do exist (password hashes, Agent credentials, session material) SHALL be protected with appropriate hashing/encryption and access control. | Design + config review. |

---

## U. Logs

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-LOG-NOSECRET | Application logs SHALL NOT contain passwords, session identifiers in raw form, Agent enrollment tokens, or customer DB credentials. | Automated redaction tests on sample paths. |
| SR-LOG-TENANT | Logs that include tenant identifiers SHOULD be safe for operator access without exporting customer DB row contents by default. | Sampling review. |

---

## V. Customer DB least privilege

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-DB-LEAST | Documentation and Agent defaults SHALL instruct/configure least-privilege DB roles sufficient for approved operations (avoid SUPERUSER/owner); V1 SHOULD fail connectivity checks that detect obviously excessive privileges when detectable. | Doc + optional privilege probe. |
| SR-INJECT-01 | Search/filter/view and Action parameter binding SHALL use safe driver APIs; injection payloads SHALL not alter query structure. | OWASP-style injection cases. |
| SR-SEARCH-01 | Search commands from Cloud SHALL be structured allowlisted operations, not arbitrary SQL/Mongo strings from the client. | Client sends SQL string → rejected. |

---

## W. Rate limiting / blast radius

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-RL-AUTH | Authentication and token-issuance endpoints SHALL be rate-limited. | Burst test. |
| SR-BLAST-01 | Mutating Actions SHALL declare an upper bound on affected records where fan-out is possible; Agent SHALL refuse unbounded mutations unless Founder+Security accept an exception. | Over-limit Action → abort. |
| SR-BLAST-02 | Cloud SHOULD rate-limit mutation executions per actor/org/env. | Burst executes throttled. |
| SR-QUEUE-LIMIT | Offline command queues SHALL have max depth and/or TTL; oldest or rejected overflow behavior SHALL be defined and safe (prefer fail closed for mutations). | Fill queue beyond limit. |
| SR-QUEUE-REAUTH | Before executing a queued mutation, Cloud/Agent SHALL ensure the issuing principal’s authorization and Action are still valid; if revoked, command SHALL NOT mutate. | Revoke role while queued → no mutate. |
| SR-QUEUE-SCOPE | Queue drain SHALL be scoped to the authenticated Agent’s org+env identity only. | Cross-tenant drain impossible. |
| SR-CONN-BIND | Every DB-targeting command SHALL include `connection_id`; Agent SHALL execute only if that connection is locally configured for this Agent and ready. | Wrong connection_id → reject. |
| SR-CONN-BLAST | Security documentation SHALL disclose multi-connection blast radius; runbooks SHALL include per-connection credential rotation after Agent compromise. | Doc presence check. |
| SR-CONN-HEALTH | Connection readiness reported to Cloud SHALL be non-secret and SHALL be refreshable; Cloud SHALL NOT assume forever-ready. | Mark not-ready → commands fail fast. |

---

## X. UI / XSS baseline (implementation epics)

| ID | Requirement | QA verification sketch |
| --- | --- | --- |
| SR-UI-XSS | Cloud UI SHALL encode untrusted data for HTML context; Action names/params reflected in UI SHALL NOT execute scripts. | Basic XSS payloads in Action name. |

---

## Y. Traceability

| Threat model IDs (selected) | Requirements |
| --- | --- |
| T-TEN-01..04 | SR-TEN-* |
| T-ENV-01..03 | SR-ENV-* |
| T-AG-01..07 | SR-AGENT-*, SR-XPORT-*, SR-CMD-*, SR-REPLAY, SR-QUEUE-* |
| T-ENR-* | SR-ENROLL-* |
| T-CONN-* | SR-CONN-* |
| T-ACT-* | SR-ACT-*, SR-FIELD-*, SR-PREV-*, SR-CONF-*, SR-EXEC-*, SR-RB-*, SR-AUDIT-*, SR-BLAST-* |
| T-DB-* | SR-SECRET-*, SR-DB-LEAST, SR-INJECT-*, SR-SEARCH-* |
| T-AUTH-*/T-PLAT-* | SR-AUTH-*, SR-SESS-*, SR-LOG-*, SR-AUDIT-IMMUT |

---

## Z. Non-goals / explicit handoffs

- **Protocol choice (mTLS vs signed JWT vs other):** Architect Issue #3 — must satisfy §K properties.
- **Domain schema details:** Issue #6 — must not violate these SHALLs.
- **Implementation code:** future epics under Security gates in the threat model.
- **Founder exceptions:** only via recorded placeholders (see threat model FA-*).

---

## Document history

| Date | Change |
| --- | --- |
| 2026-09-18 | Initial Security Engineer draft for Issue #5 |
