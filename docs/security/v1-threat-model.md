# TinyAdmin V1 Threat Model

**Status:** Draft for Architect feasibility review + QA testability review  
**Issue:** [#4](https://github.com/balarajeai/tinyadmin/issues/4)  
**Author role:** Security Engineer  
**Date:** 2026-09-18 (America/Chicago)  
**Inputs:** `docs/product/v1-requirements.md`; PR #10 architecture (`docs/architecture/v1-system-architecture.md`, ADR 0001–0005)  
**Companion:** [v1-security-requirements.md](./v1-security-requirements.md)

This model is adversarial design input. It does **not** approve implementation, production deploy, or claim TinyAdmin is secure. Unresolved CRITICAL/HIGH threats require a mapped control in [v1-security-requirements.md](./v1-security-requirements.md) or an explicit Founder risk-acceptance placeholder below.

---

## 1. Scope

### In scope (V1)

- TinyAdmin Cloud (modular monolith control plane)
- Customer Agent (data plane) enrollment, identity, outbound session, command handling
- Multi-tenant organizations; users in multiple orgs
- Environment isolation (production vs staging and similar)
- Connection metadata in Cloud; DB credentials only on Agent
- Safe Actions and configured approved-field edits (preview / confirm / execute / rollback / audit)
- Schema discovery, record search/view via Agent
- Offline Agent command queues
- Audit integrity and retention (≥1 year design target)

### Out of scope

- Full penetration test / production probing
- Contabo / production infrastructure hardening checklist execution
- Billing / payments
- Enterprise SSO/SAML (must not be blocked; not modeled as V1 auth path)
- AI-generated mutations
- Claiming whole-system Security PASS

---

## 2. Assets

| ID | Asset | Sensitivity | Location |
| --- | --- | --- | --- |
| A1 | Customer DB credentials (passwords, connection URIs with secrets) | CRITICAL | Customer infra / Agent local secrets only — **never Cloud** |
| A2 | Customer business data accessed via Agent (PII, financial, operational records) | HIGH–CRITICAL | Customer DB; transient in Agent/Cloud response path |
| A3 | TinyAdmin user credentials (passwords, password-reset tokens) | HIGH | Cloud `identity` |
| A4 | User sessions / session tokens | HIGH | Cloud (PostgreSQL per ADR 0003); browser cookie/storage |
| A5 | Agent enrollment secrets / long-lived Agent credentials | CRITICAL | Cloud `agentcontrol` + Agent local store |
| A6 | Agent identity binding (org, environment, agent_id) | CRITICAL | Cloud registry |
| A7 | Action definitions & approved-field edit configurations | HIGH | Cloud `actions` |
| A8 | Authorization decisions / RBAC bindings | HIGH | Cloud `rbac` |
| A9 | Command queue / outbox (pending mutations, search, discovery) | HIGH | Cloud PostgreSQL |
| A10 | Audit history (mutation evidence, correlation ids) | HIGH | Cloud `audit` — integrity + confidentiality of contents |
| A11 | Cached schema/collection metadata (non-secret) | MEDIUM | Cloud `discovery` |
| A12 | Connection metadata (non-secret descriptors, Agent/env refs) | MEDIUM | Cloud `connections` |
| A13 | Organization / membership / environment registry | HIGH | Cloud `tenancy`, `environments` |

---

## 3. Actors

| ID | Actor | Trust | Notes |
| --- | --- | --- | --- |
| U1 | Anonymous internet user | Untrusted | Can hit public auth endpoints |
| U2 | Authenticated support / ops / CS user | Low–medium | Primary product user; must not get DB creds or arbitrary SQL |
| U3 | Tenant admin (org admin) | Medium | Invites users, configures connections/Agents/Actions (subject to RBAC) |
| U4 | TinyAdmin platform operator / support | Medium–high | Must not receive customer DB credentials; must not alter audit history |
| AT1 | Attacker with stolen SaaS session | Hostile | Browser session theft, XSS-assisted |
| AT2 | Cross-tenant attacker (valid user in Org A) | Hostile | BOLA/IDOR against Org B |
| AT3 | Compromised or malicious Agent host | Hostile | Holds multiple DB credentials possible |
| AT4 | Network attacker on path (MITM) | Hostile | Between browser↔Cloud or Agent↔Cloud |
| AT5 | Malicious insider with Cloud DB access | Hostile | Direct datastore access |
| AT6 | Attacker with stolen enrollment token | Hostile | Agent impersonation window |
| S1 | Cloud modules (internal) | Partially trusted | Must not treat “internal” as authenticated across planes |
| S2 | Agent process | Partially trusted | Authenticated to Cloud; not trusted to invent authz |

---

## 4. Entry points

1. Browser / UI → Cloud HTTPS API (auth, invites, password reset, CRUD of tenant resources, Action workflows)
2. Cloud session cookie / bearer token on every authenticated API
3. Invitation accept links / password-reset links (email-borne secrets)
4. Agent outbound connect / enrollment / heartbeat / command pull or stream (#3)
5. Agent local secret configuration (customer ops / secret store) — out of TinyAdmin Cloud path but in threat model
6. Agent → customer PostgreSQL/MongoDB drivers
7. Cloud PostgreSQL (sessions, outbox, audit, tenant data) — operators, backups, migrations
8. Structured logs / metrics / error payloads (exfil and secret leakage)
9. Offline command queue growth and drain when Agent returns

---

## 5. Trust boundaries

| ID | Boundary | Description |
| --- | --- | --- |
| B1 | User/browser ↔ Cloud | Always untrusted client. Authn + server-side authz + tenant/env binding on every sensitive call. |
| B2 | Cloud internal tenant boundaries | Org A data must be unreachable from Org B context. Client-supplied `organizationId` is never authorization. |
| B3 | Production ↔ staging (environment) | Connections, Agents, Action execution context must not silently cross environments. |
| B4 | Cloud ↔ Agent | Separate trust domains. Outbound from Agent only. Encrypted transport. Mutual/authenticated Agent identity. Command authenticity + replay resistance (#3 must satisfy Security properties). |
| B5 | Agent enrollment | One-time or short-lived enrollment proof → long-lived Agent credential binding to org+env. |
| B6 | Agent identity | `agent_id` bound to exactly one organization and exactly one environment (V1 default). |
| B7 | Agent ↔ customer database | Local credentials; least-privilege DB roles; no Cloud involvement in credential storage. |
| B8 | Multiple DB connections behind one Agent | Connection confusion and blast-radius boundary inside one Agent process. |
| B9 | Safe Action lifecycle | Define → authorize → preview → confirm → execute → audit → optional rollback. Each step is a control point. |
| B10 | Approved-field edits | Configured allowlist only; never generic editor. |
| B11 | Preview / execution / rollback / audit | Preview read-only; execute/rollback mutating; audit append-oriented and non-repudiable for ops users. |
| B12 | Offline Agent command queues | Commands waiting while Agent disconnected; stale/dangerous command risk. |

Architecture reference: PR #10 `v1-system-architecture.md` §6.1–6.2, ADR 0002.

---

## 6. Attacker capabilities (assumptions)

- AT1 can call any Cloud API the stolen session can call until revocation.
- AT2 knows or guesses UUIDs for other tenants’ resources (BOLA).
- AT3 can read local Agent secrets, all configured connection credentials, and forge outbound traffic if Agent credential is extractable.
- AT4 can observe and modify cleartext; cannot break modern TLS without endpoint compromise.
- AT5 can UPDATE/DELETE rows if DB roles allow — motivates append-only audit controls and least privilege for app roles.
- AT6 can complete enrollment if token is single-use-broken or long-lived.

Security assumptions (must hold or be explicitly accepted):

1. Customers deploy Agent on infrastructure they control and protect.
2. Cloud TLS termination and certificate management are correctly operated (infra later).
3. Email delivery for invites/resets is available; email compromise is a residual identity risk.
4. Issue #3 will select a protocol that meets §8 properties (not chosen here).

---

## 7. Abuse cases (product-shaped)

| ID | Abuse case |
| --- | --- |
| X1 | Support user in Org A reads or mutates Org B data (tenant escape / BOLA) |
| X2 | Staging Action executed against production connection (environment confusion) |
| X3 | User runs arbitrary SQL/Mongo via crafted search or Action parameters |
| X4 | Mass mutation / fan-out Action without blast-radius limits |
| X5 | Preview leaks sensitive rows to unauthorized role |
| X6 | Misleading preview then execute against changed state (TOCTOU) |
| X7 | Rollback presented when unsafe; or rollback without authz/audit |
| X8 | Stolen enrollment token → attacker Agent joins customer org |
| X9 | Replay execute command → duplicate mutation |
| X10 | Compromised Agent used as confused deputy against wrong connection |
| X11 | Ops user deletes/edits audit rows to hide misuse |
| X12 | Offline queue drains stale “Disable User” after intent withdrawn |
| X13 | Approved-field config widened into near-generic DB editor |
| X14 | Secrets (DB password, session, Agent token) appear in logs/audit/errors |

---

## 8. Threats and mitigations

Severity: `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`. Each CRITICAL/HIGH has a **Required control** (mapped to SHALL IDs in requirements doc) or **Founder acceptance placeholder**.

### 8.1 B1 — User/browser ↔ Cloud

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-AUTH-01 | S | Credential stuffing / weak passwords on email-password auth | HIGH | SR-AUTH-*; rate limit login; password policy; secure password storage |
| T-AUTH-02 | S | Session fixation / theft / missing rotation on privilege change | HIGH | SR-SESS-* |
| T-AUTH-03 | S | Password-reset / invite token theft or reuse | HIGH | SR-INV-*, SR-RESET-*; single-use, short TTL, bound to account |
| T-AUTH-04 | T | CSRF on state-changing cookie session APIs | HIGH | SR-AUTH-CSRF |
| T-AUTH-05 | I | XSS leading to session exfil | HIGH | SR-UI-XSS; output encoding; CSP baseline in impl epics |
| T-AUTH-06 | D | Auth endpoint DoS | MEDIUM | SR-RL-AUTH |

### 8.2 B2 — Cloud tenant isolation

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-TEN-01 | E | BOLA/IDOR: resource ID without org membership check | CRITICAL | SR-TEN-*; server-side org from trusted session membership; deny by default |
| T-TEN-02 | E | Client-supplied `organizationId` trusted as authz | CRITICAL | SR-TEN-02; never trust client tenant id |
| T-TEN-03 | I | Cross-tenant leakage via search/discovery/audit export | CRITICAL | SR-TEN-*, SR-AUDIT-READ |
| T-TEN-04 | E | User in multiple orgs acts in Org A while session sticky to Org B incorrectly | HIGH | SR-TEN-04 active-org binding on every request |

### 8.3 B3 — Environment isolation

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-ENV-01 | E | Production connection selected while UI shows staging (or reverse) | CRITICAL | SR-ENV-*; every command carries Cloud-bound env; Agent rejects mismatch |
| T-ENV-02 | E | Same Agent registered for both prod and staging without hard isolation | HIGH | SR-ENV-02; V1: Agent bound to exactly one environment (aligns with product default topology) |
| T-ENV-03 | T | Connection metadata retargeted across environments after Action defined | HIGH | SR-ENV-03; immutable env on connection; Action execution re-checks env |

### 8.4 B4 — Cloud ↔ Agent transport & commands

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-AG-01 | S | Agent impersonation (stolen long-lived credential) | CRITICAL | SR-AGENT-AUTH-*; rotation + revocation; detect duplicate identity |
| T-AG-02 | T | Command forgery / tampering in transit or at rest in queue | CRITICAL | SR-XPORT-*; SR-CMD-AUTH — Issue #3 must pick mechanism satisfying properties |
| T-AG-03 | T | Replay of execute/rollback commands | CRITICAL | SR-REPLAY-*; SR-IDEM-* |
| T-AG-04 | E | Confused deputy: Agent executes command lacking Cloud authz binding | CRITICAL | SR-CMD-TICKET — Cloud-issued operation authorization bound to org/env/agent/connection/action |
| T-AG-05 | I | Eavesdropping on Agent↔Cloud | HIGH | SR-XPORT-TLS (encrypted transport property; protocol choice → #3) |
| T-AG-06 | D | Command queue unbounded while Agent offline | HIGH | SR-QUEUE-* |
| T-AG-07 | E | Cloud initiates inbound connection to customer network | CRITICAL | Already Founder-hard: outbound-only; SR-XPORT-OUTBOUND — **no exception without Founder+Security** |

**Issue #3 acceptable options (Architect picks ONE design that meets all properties):** e.g. mTLS device certificates; or mutually authenticated TLS + signed command envelopes; or enrollment-bound asymmetric key with signed JWTs for commands — **Security does not select the protocol here**. Required properties are in requirements § Agent transport.

### 8.5 B5–B6 — Enrollment & Agent identity

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-ENR-01 | S | Enrollment token theft → rogue Agent | CRITICAL | SR-ENROLL-*; short TTL, single-use, org+env pre-bind, audit |
| T-ENR-02 | E | Re-binding Agent to different org/env after activation | CRITICAL | SR-AGENT-BIND immutable org+env after activation |
| T-ENR-03 | R | Unaudited enrollment / revocation | HIGH | SR-ENROLL-AUDIT |

### 8.6 B7 — Agent ↔ customer DB

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-DB-01 | I | DB credential theft from Agent host | CRITICAL | SR-SECRET-AGENT; customer-side secret store guidance; no Cloud storage |
| T-DB-02 | E | Agent DB role overly privileged (DDL, DROP, SUPERUSER) | HIGH | SR-DB-LEAST |
| T-DB-03 | T | Injection via unsafely concatenated search/Action parameters | CRITICAL | SR-INJECT-*; parameterized driver APIs only; no arbitrary SQL/Mongo from UI |
| T-DB-04 | I | Support user obtains credentials through TinyAdmin UI/API/logs | CRITICAL | SR-SECRET-CLOUD-DENY; SR-LOG-NOSECRET |

### 8.7 B8 — Multiple connections per Agent

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-CONN-01 | E | Connection confusion: command for conn A executed on conn B | CRITICAL | SR-CONN-BIND; Agent verifies connection_id locally configured + ready |
| T-CONN-02 | E | Single Agent compromise exposes all connection credentials | HIGH | SR-CONN-BLAST; document residual risk; prefer least privilege per connection; **Founder acceptance placeholder** if multi-connection default retained without host isolation |
| T-CONN-03 | T | Stale readiness: Cloud believes connection ready after local secret removed | MEDIUM | SR-CONN-HEALTH |

**Founder acceptance placeholder — FA-CONN-BLAST:**  
If V1 ships default “one Agent / multiple connections” without additional host isolation, residual HIGH risk T-CONN-02 remains.  
- Accepting human: _TBD_  
- Date: _TBD_  
- Reason: _TBD_  
- Expiry / follow-up: _TBD (e.g. per-connection credential isolation guidance in runbooks)_  
Agents MUST NOT invent acceptance.

### 8.8 B9–B11 — Safe Actions, field edits, preview, execute, rollback, audit

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-ACT-01 | E | Unauthorized Action define vs run (privilege escalation) | HIGH | SR-RBAC-ACT-* |
| T-ACT-02 | E | Action catalog / field-edit config used cross-tenant | CRITICAL | SR-TEN-* on Action resources |
| T-ACT-03 | T | Preview mutates DB | CRITICAL | SR-PREV-READONLY |
| T-ACT-04 | I | Preview overclaims authority / hides limitations | HIGH | SR-PREV-HONEST |
| T-ACT-05 | T | TOCTOU: execute against state different from preview | HIGH | SR-PREV-TOCTOU (re-validate or bind snapshot + disclose) |
| T-ACT-06 | E | Execute without confirmation / without production step-up policy | HIGH | SR-CONF-* |
| T-ACT-07 | T | Duplicate execution under at-least-once delivery | CRITICAL | SR-IDEM-* |
| T-ACT-08 | E | Rollback when not reversible / unsafe state | HIGH | SR-RB-* |
| T-ACT-09 | R | Mutation attempt without audit (success or fail) | HIGH | SR-AUDIT-MUT |
| T-ACT-10 | T | Audit tampering by ops roles | CRITICAL | SR-AUDIT-IMMUT |
| T-ACT-11 | E | Approved-field edit becomes generic editor | HIGH | SR-FIELD-* |
| T-ACT-12 | D | Excessive mutation blast radius (unbounded fan-out) | HIGH | SR-BLAST-* |
| T-ACT-13 | I | Before/after audit payloads leak secrets | HIGH | SR-AUDIT-REDACT |

### 8.9 B12 — Offline queues

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-Q-01 | T | Stale mutating command executes after admin revoked Action/user | HIGH | SR-QUEUE-REAUTH; cancel on revoke; TTL |
| T-Q-02 | D | Queue growth DoS / storage exhaustion | MEDIUM | SR-QUEUE-LIMIT |
| T-Q-03 | E | Wrong Agent drains another org’s commands | CRITICAL | SR-QUEUE-SCOPE tenant+agent binding |

### 8.10 Cloud datastore / logs / platform

| ID | STRIDE | Threat | Sev | Required control / acceptance |
| --- | --- | --- | --- | --- |
| T-PLAT-01 | I | Secrets in structured logs or exception messages | HIGH | SR-LOG-NOSECRET |
| T-PLAT-02 | T | App DB role can UPDATE/DELETE audit rows | CRITICAL | SR-AUDIT-IMMUT (DB grants / triggers / separate role) |
| T-PLAT-03 | I | Backup/export of Cloud DB includes session hashes & Agent secrets at rest | HIGH | SR-SECRET-ATREST; access control on backups (impl/infra epic) |

---

## 9. Residual risks

| ID | Risk | Sev | Disposition |
| --- | --- | --- | --- |
| R1 | Agent host compromise ⇒ all local DB credentials and mutate capability | HIGH | Mitigate with least privilege + revoke; residual → **FA-CONN-BLAST** if multi-connection retained |
| R2 | Email account compromise ⇒ invite/reset abuse | MEDIUM | Standard email-borne risk; rate limit + TTL; no Founder exception needed for V1 awareness |
| R3 | Preview/execute race even with re-read | MEDIUM | Disclose non-authoritative preview; optional Founder UX acceptance if product prioritizes speed over strict binding |
| R4 | Malicious insider with Cloud PostgreSQL superuser | HIGH | Operational control / infra; out of app-only mitigations — track in infra security later |
| R5 | Protocol (#3) not yet chosen | HIGH | Architecture FAIL findings until normative properties locked; not a waiver |

**Founder acceptance placeholder — FA-PREV-RACE (optional):**  
If product accepts residual TOCTOU after mandatory re-validation controls are implemented.  
- Accepting human: _TBD_ — Date: _TBD_ — Reason: _TBD_ — Follow-up: _TBD_

---

## 10. Security gates for implementation epics

These gates apply before merge/release of related implementation work (DoD / CoS cannot waive Security where triggered):

| Gate ID | Applies to | Requirement |
| --- | --- | --- |
| SG-1 | Any authn/session/invite/reset change | Security review + tests for SR-AUTH/SESS/INV/RESET |
| SG-2 | Any tenant-scoped API / query | Automated tenant isolation tests (cross-org deny); Security review on first introduction |
| SG-3 | Environment-scoped connections/Agents/Actions | Env isolation tests (prod≠staging); UI distinction evidence |
| SG-4 | Agent enrollment / authn / transport (#3 impl) | Security review of chosen protocol against SR-XPORT/AGENT/ENROLL; no ad-hoc feature-PR invention |
| SG-5 | Command execute / rollback paths | Idempotency + replay tests; operation-ticket binding tests |
| SG-6 | Preview paths | Prove non-mutating at Agent/DB; honesty flags tested |
| SG-7 | Action / approved-field definition | RBAC split define-vs-run; allowlist enforcement tests |
| SG-8 | Audit module | Append-only enforcement test; retention job design; ops-deny mutate |
| SG-9 | Logging | Secret-scanning / redaction tests on sample error paths |
| SG-10 | Customer DB access from Agent | No Cloud credential storage tests; parameterized query review |

Unresolved HIGH/CRITICAL findings on these epics **block release** unless human exception recorded under AI_AGENT_POLICY.

---

## 11. Mapping to V1 capabilities

| Capability | Primary threats | Primary requirements |
| --- | --- | --- |
| Auth / invites / reset / sessions | T-AUTH-* | SR-AUTH-*, SR-SESS-*, SR-INV-*, SR-RESET-* |
| Orgs / RBAC | T-TEN-*, T-ACT-01 | SR-TEN-*, SR-RBAC-* |
| Environments | T-ENV-* | SR-ENV-* |
| Agent register / heartbeat | T-ENR-*, T-AG-* | SR-ENROLL-*, SR-AGENT-* |
| Connections | T-CONN-*, T-DB-* | SR-CONN-*, SR-SECRET-*, SR-DB-LEAST |
| Discovery / search | T-DB-03, T-TEN-* | SR-INJECT-*, SR-TEN-*, SR-SEARCH-* |
| Safe Actions / field edits | T-ACT-* | SR-ACT-*, SR-FIELD-*, SR-BLAST-* |
| Preview / confirm / execute | T-ACT-03–07 | SR-PREV-*, SR-CONF-*, SR-IDEM-* |
| Rollback | T-ACT-08 | SR-RB-* |
| Audit | T-ACT-09–10, T-PLAT-02 | SR-AUDIT-* |

---

## 12. Document history

| Date | Change |
| --- | --- |
| 2026-09-18 | Initial Security Engineer draft for Issue #4 |
