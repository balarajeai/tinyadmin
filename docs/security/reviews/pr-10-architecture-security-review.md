# Security Review

## Review ID

`SEC-REV-TA-PR10-2026-09-18`

## Component

TinyAdmin V1 system architecture package (Issue #2) — documentation only

## Change

Task ID: Issue #2 — Produce TinyAdmin V1 system architecture  
PR: [#10](https://github.com/balarajeai/tinyadmin/pull/10) — `docs(architecture): TinyAdmin V1 system architecture (#2)`  
Branch: `docs/issue-2-v1-system-architecture`  
Risk level: `CRITICAL`

### Files reviewed (PR head)

| Path | Role |
| --- | --- |
| `docs/architecture/README.md` | Index |
| `docs/architecture/v1-system-architecture.md` | Primary architecture |
| `docs/architecture/adr/0001-modular-monolith-cloud.md` | ADR |
| `docs/architecture/adr/0002-control-plane-vs-agent-data-plane.md` | ADR |
| `docs/architecture/adr/0003-cloud-datastore-postgres-redis-deferred.md` | ADR |
| `docs/architecture/adr/0004-agent-technology-go.md` | ADR |
| `docs/architecture/adr/0005-cloud-stack-java-spring.md` | ADR |

Fetched read-only via `gh api` from ref `docs/issue-2-v1-system-architecture`. No application code in this PR.

### Review scope statement

This review evaluates whether PR #10 provides **acceptable trust-boundary inputs for Issue #2** at the architecture-documentation stage.  

**This review does NOT claim the entire TinyAdmin system is secure.** Protocol (#3), domain model (#6), implementation, and production infra are out of scope for a PASS on the whole product.

---

## Assets

Customer DB credentials (must never enter Cloud); Agent enrollment/identity credentials; tenant org data; Action definitions; command outbox; audit integrity; end-user sessions; transient customer record payloads crossing Agent↔Cloud.

## Trust boundaries

Per architecture §6.1 and Security threat model: User↔Cloud; Cloud tenant isolation; environment isolation; Cloud↔Agent; Agent enrollment/identity; Agent↔DB; multi-connection Agent; Safe Action lifecycle; preview/execute/rollback/audit; offline queues.

---

## Authentication

`FAIL` — Human auth is named (email/password, sessions, invites, reset) at module level (architecture §2.1, §3.1 `identity`, ADR 0005) but PR #10 does not state session security properties or enrollment authentication **properties** as normative architecture constraints for downstream #3. Acceptable as partial input; insufficient as complete boundary specification for Agent auth. See findings SEC-PR10-001, SEC-PR10-003.

## Authorization

`FAIL` — Server-side RBAC is asserted (architecture §1 table, §2.1, sequences 5.4–5.8, AC checklist §8) but missing normative **operation authorization ticket** binding that Agent must enforce (confused deputy). Define-vs-run RBAC split not architecturalized. See SEC-PR10-003, SEC-PR10-009.

## Tenant isolation

`PASS` *(design intent)* — Architecture correctly marks cross-tenant access as critical and requires server-side org filters (§6.1 #4, §8 AC). **Gap:** AC checklist does not explicitly forbid trusting client-supplied org IDs (NON-BLOCKING hardening). See SEC-PR10-007.

## Injection

`PASS` *(design intent)* — No arbitrary SQL/Mongo; structured commands; driver-native per DB (ADR 0002, §1, §8). Implementation still required; architecture stance is acceptable.

## Secrets

`PASS` *(design intent)* — Hard rule: credentials never in Cloud; never open customer DB ports; outbound Agent (ADR 0002, §2.3, §8). Aligns with Founder lock. Logging “no secrets” in AC. Residual: multi-connection Agent blast radius. See SEC-PR10-005.

## Logging

`N/A` — No logging implementation in this PR. Architecture AC requires secrets never in structured logs (§8) — acceptable placeholder for Issue #2.

## Data exposure

`FAIL` — Preview/search return customer data across Cloud; architecture lacks normative TOCTOU / preview honesty enforcement beyond narrative risk notes. See SEC-PR10-004. Audit export controls deferred (expected) but append-only mechanism underspecified (NON-BLOCKING). See SEC-PR10-006.

## Dependencies

`N/A` — No dependency manifest changes in this docs PR. Go Agent + Java Cloud ADRs are technology choices without supply-chain evidence (expected at this stage).

## Network impact

`PASS` *(design intent)* — Outbound-only Agent; Cloud never dials customer DB ports (ADR 0002). Correct Founder alignment. Issue #3 must still prove encrypted mutually authenticated transport properties.

---

## Findings

### Finding

- ID: `SEC-PR10-001`
- Title: Cloud↔Agent boundary lacks normative security properties for Issue #3
- Severity: `HIGH`
- Classification: **BLOCKING**
- Affected component: `v1-system-architecture.md` §2.3, §5 (all `#3` notes), §6.1 #2, §6.2; ADR 0002 “owned by Issue #3”
- Attack path: Architect (#3) or implementers select a weak Agent authn/command model (shared static token, no replay protection, no command integrity) because PR #10 only lists threats as bullets, not mandatory properties.
- Reproduction (design-level): ADR 0002 and architecture §2.3 defer transport/authn/delivery entirely to #3 without a normative “MUST satisfy” property list (mutual auth, command authenticity, replay resistance, outbound-only, encryption, identity binding). §6.2 bullets are informative, not acceptance criteria.
- Impact: High likelihood of insecure-by-default protocol ADR; rework or CRITICAL runtime defects (impersonation, forgery, replay).
- Recommended remediation: Add an architecture subsection (or ADR 0002 amendment) listing **required security properties** for Cloud↔Agent that Issue #3 MUST satisfy, without selecting mTLS vs JWT vs other. Align with Security requirements SR-XPORT-*, SR-CMD-*, SR-REPLAY, SR-AGENT-AUTH-*.
- Verification method: Diff shows normative property checklist; #3 ADR references and claims conformance to each property.
- Status: `OPEN`

### Finding

- ID: `SEC-PR10-002`
- Title: Environment isolation binding rules not normative (Agent/env/connection)
- Severity: `HIGH`
- Classification: **BLOCKING**
- Affected component: `v1-system-architecture.md` §3.1 `environments`, §6.1 #5, §7 multi-connection risk, §8 “meaningful isolation”; product default “one Agent per environment/network”
- Attack path: Operator registers one Agent used for both staging and production connections; soft labels fail; Action intended for staging mutates production (environment confusion).
- Reproduction: Architecture requires “meaningful isolation” but never states whether an Agent MAY span environments, whether env is immutable on connection, or that Agent MUST reject cross-env command tickets. Product V1 default topology is not made an architectural invariant.
- Impact: CRITICAL class failure mode (prod/staging silent cross) left to implementer invention.
- Recommended remediation: Normative rules: (1) Agent bound to exactly one org + one env at activation for V1; (2) connection.env immutable; (3) command tickets carry env; Agent rejects mismatch. If Architect proposes multi-env Agent, document hard isolation controls and request Security re-review.
- Verification method: Updated architecture AC checkboxes; sequence notes updated; #3/#6 consume invariants.
- Status: `OPEN`

### Finding

- ID: `SEC-PR10-003`
- Title: Missing Cloud-issued operation authorization binding (confused deputy)
- Severity: `HIGH`
- Classification: **BLOCKING**
- Affected component: Sequences §5.3–5.8; `agentcontrol` module; §6.2 “command injection or privilege escalation via malformed Agent commands”
- Attack path: Attacker who compromises Agent session material—or a bug that allows injecting queue rows—causes Agent to execute mutations without a Cloud authz decision bound to actor/org/env/connection/action/expiry/operation id.
- Reproduction: Execute sequence (§5.6) shows `Act->>RBAC: Authorize` then `Enqueue execute command` but does not require the dispatched command to carry a verifiable authorization ticket that Agent validates. No AC item requires Agent-side binding checks.
- Impact: Confused deputy / unauthorized mutation against customer DB; breaks control-plane authz guarantee at data plane.
- Recommended remediation: Require Cloud-issued operation authorization (ticket/envelope) with listed bindings; Agent MUST reject unbound/invalid/expired commands. Protocol encoding left to #3.
- Verification method: Architecture AC + #3 ADR include ticket fields and Agent reject behavior; future tests per SR-CMD-TICKET.
- Status: `OPEN`

### Finding

- ID: `SEC-PR10-004`
- Title: Preview→execute TOCTOU control not required
- Severity: `HIGH`
- Classification: **BLOCKING**
- Affected component: §5.5–5.6 sequences; §7 “Preview non-authoritative”; §8 preview AC (read-only only)
- Attack path: User previews record state; concurrent change occurs; user confirms execute; mutation applies against different state than previewed (stale preview / TOCTOU), possibly with UI still showing prior preview as authoritative.
- Reproduction: Architecture correctly says preview must not over-claim (§5.5 note, §7) but §8 acceptance criteria only require “Preview paths are read-only” — no requirement for execute-time re-validation, preview snapshot token, or mandatory limitation flag propagation into the confirm/execute UX contract.
- Impact: Incorrect production mutations; support users misled; violates Founder preview honesty principle in practice.
- Recommended remediation: Add AC: execute MUST re-validate preconditions at Agent (or bind short-lived preview token) AND non-authoritative flags MUST flow to confirmation UI. Optional Founder acceptance only for residual race after controls exist.
- Verification method: Architecture AC updated; mapped to SR-PREV-TOCTOU / SR-PREV-HONEST.
- Status: `OPEN`

### Finding

- ID: `SEC-PR10-005`
- Title: Multi-connection Agent blast radius acknowledged without required controls
- Severity: `HIGH`
- Classification: **NON-BLOCKING** *(for Issue #2 doc completeness if Founder acceptance tracked)*
- Affected component: §7 “Multi-connection Agent blast radius”; ADR 0002 topology
- Attack path: Compromise of one Agent host yields credentials and mutate capability for all configured connections.
- Reproduction: Risk table lists the issue for Security to “weigh” but architecture does not require per-connection least privilege guidance, revoke-all-on-compromise runbook hook, or Founder residual acceptance placeholder in-repo.
- Impact: HIGH residual; product default may be acceptable with disclosure + controls.
- Recommended remediation: Document required compensating controls (SR-CONN-BLAST, SR-DB-LEAST) and add Founder acceptance placeholder if default retained (see threat model FA-CONN-BLAST). Not required to redesign topology in this PR if explicitly accepted.
- Verification method: Architecture risk row links to control requirements + FA placeholder.
- Status: `OPEN`

### Finding

- ID: `SEC-PR10-006`
- Title: Audit append-only integrity mechanism unspecified
- Severity: `MEDIUM`
- Classification: **NON-BLOCKING**
- Affected component: §5.7; §6.1 #6; §8 audit AC; ADR 0003 follow-ups
- Attack path: Privileged ops or compromised app DB credentials UPDATE/DELETE audit rows to hide misuse.
- Reproduction: Docs say “append-oriented” and “ops users cannot update/delete” but specify no enforcement approach (DB privileges, triggers, immutability store, WORM). Acceptable to refine in #4/#5/#6 **if** called out as open design point — currently easy to implement as soft application check only.
- Impact: Audit non-repudiation weaker than product promise.
- Recommended remediation: State that storage-level enforcement is required (not UI-only) and point to Security requirements SR-AUDIT-IMMUT; Architect/#6 choose mechanism.
- Verification method: Architecture note + domain model constraint.
- Status: `OPEN`

### Finding

- ID: `SEC-PR10-007`
- Title: AC checklist omits explicit ban on client-provided tenant IDs as authorization
- Severity: `MEDIUM`
- Classification: **NON-BLOCKING**
- Affected component: §8 Architecture acceptance criteria; §6.1 #4
- Attack path: Implementer adds `organizationId` from request body as the tenant filter source → BOLA.
- Reproduction: §6.1 states server-side filtering; §8 says “Tenant (org) filters enforced server-side” but never states “client-provided org id is never authorization.”
- Impact: Classic multi-tenant failure mode if missed in Sprint 1.
- Recommended remediation: Add explicit AC bullet mirroring Security Principles / SR-TEN-02.
- Verification method: AC checkbox present.
- Status: `OPEN`

### Finding

- ID: `SEC-PR10-008`
- Title: Offline command queue lacks TTL/depth/cancel-on-revoke requirements
- Severity: `MEDIUM`
- Classification: **NON-BLOCKING**
- Affected component: §6.2 DoS via queue growth; §7 Agent offline; sequences enqueue without lifecycle limits
- Attack path: Agent offline accumulates mutating commands; admin revokes user/Action; Agent returns and drains stale dangerous mutations; or queue exhausts storage.
- Reproduction: Risk mentioned; no AC for max depth, TTL, or re-authz at drain.
- Impact: Stale mutation / DoS.
- Recommended remediation: Add AC referencing SR-QUEUE-*; detail may live in #3.
- Verification method: AC + #3 failure-mode section.
- Status: `OPEN`

### Finding

- ID: `SEC-PR10-009`
- Title: RBAC define-vs-run and approved-field configuration authority underspecified
- Severity: `MEDIUM`
- Classification: **NON-BLOCKING**
- Affected component: §2.1 RBAC; §3.1 `rbac`/`actions`; approved-field mentions in §1/§5.6
- Attack path: Single broad “admin” role both widens approved fields to near-generic editor and executes in production without separation.
- Reproduction: Architecture mandates server-side RBAC but does not require separation of Action/field **definition** vs **execution** permissions (Founder: Actions preferred for sensitive mutations; never generic editor).
- Impact: Privilege concentration; field-edit abuse.
- Recommended remediation: Note normative RBAC capability split; details in Security requirements SR-RBAC-02/03 and SR-FIELD-* (may be finalized with #6).
- Verification method: Architecture “Security must enforce” note or pointer to `docs/security/v1-security-requirements.md`.
- Status: `OPEN`

### Finding

- ID: `SEC-PR10-010`
- Title: Connection-id binding at Agent not in acceptance criteria
- Severity: `HIGH`
- Classification: **NON-BLOCKING** *(borderline; partially implied by metadata model — elevate to BLOCKING if Architect disagrees with SR-CONN-BIND)*
- Affected component: §5.2 connection registration; §5.3–5.6 commands; §7 connection blast radius
- Attack path: Command omits or confuses `connection_id`; Agent applies mutation on wrong DB among many.
- Reproduction: Flows mention connection ids in places but §8 AC has no explicit “Agent MUST verify connection_id locally configured + ready.”
- Impact: Connection confusion → wrong-database mutation (CRITICAL in effect).
- Recommended remediation: Add AC bullet for SR-CONN-BIND; treat as required before Agent implementation.
- Verification method: AC + #3 command schema includes connection_id.
- Status: `OPEN`

---

## Remediation

| Finding | Owner | Block merge of Issue #2 DoD? |
| --- | --- | --- |
| SEC-PR10-001 | Architect (amend PR #10 or follow-up arch PR) | **Yes** |
| SEC-PR10-002 | Architect | **Yes** |
| SEC-PR10-003 | Architect (+ #3 consume) | **Yes** |
| SEC-PR10-004 | Architect | **Yes** |
| SEC-PR10-005 | Architect + Founder acceptance if needed | No (track FA) |
| SEC-PR10-006 | Architect / #6 with Security reqs | No |
| SEC-PR10-007 | Architect (small AC edit) | No |
| SEC-PR10-008 | Architect / #3 | No |
| SEC-PR10-009 | Architect pointer + Security reqs | No |
| SEC-PR10-010 | Architect AC edit | No (strongly recommended before Agent epics) |

Security Engineer will **not** rewrite architecture docs to silently absorb findings. Architect must amend or explicitly dispute with CoS/Founder.

---

## Verification

Design-level review only. No runtime reproduction (docs PR; no deployed system). Sources: PR #10 head files via `gh api` (2026-09-18). Independent of Architect authorship.

After Architect remediates BLOCKING items, a **different** agent or human MUST re-verify material fixes — Security author of those fixes cannot be sole approver.

---

## Human exception

_None invented._ Placeholders for residual multi-connection blast radius and optional preview race acceptance live in `docs/security/v1-threat-model.md` (FA-CONN-BLAST, FA-PREV-RACE) for Founder completion.

---

## Positive observations (not a PASS)

1. ADR 0002 correctly encodes Founder hard rules (no Cloud DB creds; outbound Agent; no Cloud→customer DB ports; no arbitrary SQL/Mongo).
2. Modular monolith rejection of premature microservices is sound for authz+audit transactional needs (ADR 0001).
3. Explicit `#3` ownership of delivery semantics and idempotency callouts in execute sequence are directionally correct.
4. Trust-boundary section §6 and threat bullet list are useful **inputs** — incomplete without normative properties (findings above).
5. Architecture correctly states it does **not** claim Security approval.

---

## Final result

`FAIL`

Release blocked? **Yes** — for Issue #2 architecture acceptance / DoD “Security acknowledgment” until BLOCKING findings SEC-PR10-001..004 are addressed or explicitly human-accepted.  

**Not blocked:** starting Security docs PR for #4/#5; Architect work on #3 **using** Security property list; discussion on NON-BLOCKING items.

Unresolved CRITICAL or HIGH findings MUST block release unless explicitly accepted above.  
Highest unresolved blocking severity: **HIGH** (four BLOCKING findings).  
CRITICAL severity findings: **0** (design-stage; several map to CRITICAL *threats* if shipped without fix).

### Whole-system claim

**None.** This verdict applies only to the PR #10 architecture documentation package as trust-boundary input for Issue #2.
