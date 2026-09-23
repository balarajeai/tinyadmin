# TinyAdmin V1 Security Requirements (Production-MVP)

| Field | Value |
| --- | --- |
| **Status** | Security draft for Architect enforceability + QA testability + Independent Code Review — **not** merge authority; **not** production approval |
| **Issue** | [#5](https://github.com/balarajeai/tinyadmin/issues/5) |
| **Author** | Security Engineer |
| **Mode** | Production-MVP (~testable SHALL set; not an enterprise control catalog) |
| **Date** | 2026-09-23 (America/Chicago) |
| **Inputs (established)** | Merged Issue #4 threat model `docs/security/v1-threat-model.md` @ `42323005194d1100371127cbbbe4171324587232`; product lock; architecture + ADR 0006; protocol + ADR 0007 (Issue #3); domain + ADR 0008 (Issue #6) |
| **Supersedes** | Historical Issue #5 draft material on older branches/PRs (e.g. PR #11) for the Production-MVP deliverable |

This document converts Issue #4 threats and the `SR-*` handoff into a **small, concrete, testable** set of V1 security requirements for Backend, Agent, Frontend, Platform, and QA.

It does **not** reopen approved architecture from Issues #2 / #3 / #4 / #6. It does **not** invent a full RBAC role matrix. It does **not** start Sprint 1 / product implementation.

---

## 1. Normative language

| Word | Meaning |
| --- | --- |
| **MUST / SHALL** | Mandatory for the named feature to ship |
| **MUST NOT / SHALL NOT** | Forbidden |
| **SHOULD** | Strong recommendation; deviation needs recorded rationale |
| **MAY** | Optional |

Mitigation provenance uses Issue #4 states:

| State | Meaning |
| --- | --- |
| **ESTABLISHED** | Guaranteed by merged architecture / protocol / domain |
| **IMPLEMENTATION REQUIRED** | Must be built and tested before the gated feature ships |
| **DEFERRED** | Not required for V1 MVP; residual risk accepted without Founder exception unless noted |

---

## 2. Requirements

### 2.1 Tenant isolation (SR-TENANT)

#### SR-TENANT-001
| | |
| --- | --- |
| **REQUIREMENT** | Cloud SHALL authorize access to every tenant-owned resource using the authenticated actor’s **active** `OrganizationMembership` for an explicit active organization context, and SHALL NOT treat a client-supplied organization identifier as authorization. |
| **OWNER** | Backend |
| **THREAT / SOURCE** | TM-TENANT-001; Issue #4 SR-TENANT |
| **VERIFICATION** | Integration test: valid session for Org A requesting an Org B resource by ID is denied with no resource body disclosure. Multi-org user without selecting Org B cannot access Org B resources via spoofed `organization_id`. |

#### SR-TENANT-002
| | |
| --- | --- |
| **REQUIREMENT** | Cloud resource lookups for tenant-owned entities SHALL include a server-side organization (and environment, where the resource is environment-scoped) predicate derived from membership and resource ownership. Lookups that omit the predicate or rely solely on globally unique IDs without org checks SHALL fail closed. |
| **OWNER** | Backend |
| **THREAT / SOURCE** | TM-TENANT-001 (BOLA/IDOR) |
| **VERIFICATION** | Automated cross-tenant matrix over Agents, Connections, Actions, Operations, Audit reads: foreign IDs always deny. |

---

### 2.2 Authorization (SR-AUTHZ)

#### SR-AUTHZ-001
| | |
| --- | --- |
| **REQUIREMENT** | Cloud SHALL enforce **separate** server-side permissions for Action/field-edit **definition** versus **execution**. Possessing define permission SHALL NOT grant run permission, and possessing run permission SHALL NOT grant define permission. |
| **OWNER** | Backend |
| **THREAT / SOURCE** | TM-TENANT-002; TM-ACTION-004 |
| **VERIFICATION** | API tests: define-only principal cannot preview/confirm/execute; run-only principal cannot create/update ActionDefinition or ApprovedFieldEditConfig. |

#### SR-AUTHZ-002
| | |
| --- | --- |
| **REQUIREMENT** | Cloud SHALL re-evaluate active membership status and required permissions on **every** sensitive API that creates previews, confirmations, mutation authorizations, rollbacks, Agent enrollment intents, or Connection/Action configuration changes. Disabled users and revoked memberships SHALL NOT continue privileged operations. On authorization invalidation (membership revoke, disable, permission revoke), Cloud SHALL cancel undelivered mutating commands associated with that invalidated authorization when they have not yet been dispatched to the Agent. Regardless of queue or delivery state, Cloud and Agent SHALL reject mutation execution if the authorization is no longer valid at the pre-mutation authorization check (Issue #3 command verification / cancel-revoke sync). There is **no** “not feasible” exception that permits mutation under revoked or disabled authorization. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-AUTH-003 |
| **VERIFICATION** | (1) After membership revoke mid-session, subsequent preview/confirm/mutate-mint APIs return deny. (2) Undelivered mutating outbox items for that actor are cancelled before dispatch (assert cancelled state). (3) If a mutating command was already delivered, Agent rejects execution after revoke/cancel sync and Cloud does not accept a success fabricated without Agent authorization. Pass requires all three. |

#### SR-AUTHZ-003
| | |
| --- | --- |
| **REQUIREMENT** | Permission evaluation SHALL bind to the Operation’s organization, environment, Action (or approved-field config), and Connection as resolved server-side. Read/search permission SHALL NOT authorize mutation. Rollback SHALL require its own distinct authorization (see SR-ROLLBACK-001). Administrative configuration permission SHALL NOT imply operational execution permission. |
| **OWNER** | Backend |
| **THREAT / SOURCE** | TM-TENANT-002; TM-ACTION-004; TM-ROLLBACK-001 |
| **VERIFICATION** | Matrix tests: search-capable user cannot execute; connection-admin cannot run Actions without run permission; rollback denied without rollback permission even if original actor. |

---

### 2.3 Environment isolation (SR-ENV)

#### SR-ENV-001
| | |
| --- | --- |
| **REQUIREMENT** | Before preview, confirmation, or mutation authorization, Cloud SHALL verify that Action (or approved-field config), Connection, Agent, and Operation environment identifiers are consistent with each other and with the Agent’s immutable organization/environment binding. Cross-environment substitution SHALL fail closed. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-ENV-001; TM-TENANT-003 |
| **VERIFICATION** | Attempt staging Action against production Connection (same org) is denied by Cloud; Agent rejects envelope whose environment/connection binding mismatches local Agent binding. |

#### SR-ENV-002
| | |
| --- | --- |
| **REQUIREMENT** | Frontend SHALL clearly identify **PRODUCTION** environment on confirmation UX for mutating operations. Cloud SHALL still refuse mutation authorization without a valid Confirmation record even if the client omits production UI steps. |
| **OWNER** | Frontend + Backend |
| **THREAT / SOURCE** | TM-PREVIEW-003; TM-ENV-001 |
| **VERIFICATION** | **Automated (blocking for mutate path):** API test — mutate mint without a valid Confirmation is rejected regardless of client flags. **Manual (non-blocking for this Issue #5 docs gate):** production visual distinction SHALL be verified against a named manual QA evidence item defined in Issue #7 (verification strategy). Until Issue #7 publishes that evidence item ID, record interim evidence as `QA-EVIDENCE-ENV-PROD-UX` on the relevant frontend PR. |

---

### 2.4 Agent security (SR-AGENT)

#### SR-AGENT-001
| | |
| --- | --- |
| **REQUIREMENT** | Agent enrollment tokens SHALL be single-use, short-lived, stored only as hashes at rest, displayed at most once, and never written to application logs. Enrollment SHALL pre-bind organization and environment immutably per ADR 0007. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-AGENT-001 |
| **VERIFICATION** | Token reuse after consume fails; expired token fails; DB stores hash only; log redaction scan for raw token patterns in enrollment paths. |

#### SR-AGENT-002
| | |
| --- | --- |
| **REQUIREMENT** | Each Agent SHALL have its own identity keypair. Cloud SHALL store only the public key. Agent private keys SHALL remain in customer infrastructure with restricted permissions or an OS/secret-store mechanism. Revoking an Agent SHALL invalidate sessions, reject further authentication, and cancel pending unexecuted mutating commands for that Agent. |
| **OWNER** | Agent + Backend |
| **THREAT / SOURCE** | TM-AGENT-002; TM-AGENT-003 |
| **VERIFICATION** | Revoked Agent cannot open a new session; pending mutate outbox entries cancelled; Agent rejects commands after revoke sync. |

#### SR-AGENT-003
| | |
| --- | --- |
| **REQUIREMENT** | Agent organization and environment bindings SHALL be immutable after activation. Rebind attempts SHALL fail closed. On reconnect, Agent SHALL complete authoritative cancel/revoke synchronization **before** executing any pending mutating command (Issue #3 protocol order). |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-AGENT-003; TM-CMD-003 |
| **VERIFICATION** | API/DB immutability tests; reconnect integration test refuses mutate drain when cancel sync incomplete. |

---

### 2.5 Command authorization (SR-CMD)

#### SR-CMD-001
| | |
| --- | --- |
| **REQUIREMENT** | Cloud-authorized mutation commands SHALL use the Issue #3 signed authorization envelope. The signature coverage SHALL bind at least: organization, environment, Agent, Connection, operation, actor, authorized Action/effect identity, payload digest / canonical representation, max affected records (where applicable), and expiry — using ADR 0007 canonicalization rules. Agent SHALL verify signature, digest, bindings, and expiry **before** execute and SHALL reject invalid, expired, mismatched, unknown-`kid`, or unauthorized commands fail closed. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-CMD-001; TM-CMD-004; TM-ACTION-002 |
| **VERIFICATION** | Tampered payload fails digest; wrong connection_id fails; expired envelope fails; unsigned command never executes. |

#### SR-CMD-002
| | |
| --- | --- |
| **REQUIREMENT** | Duplicate delivery or replay of the same `operation_id` SHALL NOT cause a second mutation. Agent durable execution state SHALL return the prior terminal result when known, and SHALL NOT blind-retry when outcome is indeterminate (`unknown` / reconciliation required). |
| **OWNER** | Agent + Backend |
| **THREAT / SOURCE** | TM-CMD-002 |
| **VERIFICATION** | Deliver identical mutate command twice after success → single DB effect; after indeterminate crash window → `unknown`, no second mutate. |

---

### 2.6 Discovery / search (SR-DISCOVERY) — CR-TM-001 disposition

#### SR-DISCOVERY-001
| | |
| --- | --- |
| **REQUIREMENT** | Schema discovery and record search/read SHALL require server-side authorization scoped to the actor’s active organization and the target Connection’s organization/environment. Discovery/search SHALL NOT mutate customer data and SHALL NOT return customer DB credentials, enrollment secrets, Agent private keys, or Cloud signing secrets in responses or cached metadata. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | CR-TM-001 (Independent Code Review on PR #15); TM-TENANT-001; TM-DB-001 |
| **VERIFICATION** | Cross-org discovery/search deny tests; response/fixture scan asserts no secret fields; discovery handlers use read-only DB paths. |

---

### 2.7 Safe Actions / approved-field edits (SR-ACTION)

#### SR-ACTION-001
| | |
| --- | --- |
| **REQUIREMENT** | Customer-data mutations SHALL occur only through explicitly configured Safe Actions or explicitly allowlisted approved-field edits. Browser-supplied arbitrary SQL, and browser-supplied Mongo command/query language capable of arbitrary mutation, SHALL NOT be accepted as an execution path. PostgreSQL and MongoDB MAY use engine-specific structured effect models; they SHALL NOT share an unsafe common free-form query console. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-ACTION-001; TM-DB-002 |
| **VERIFICATION** | API rejects raw SQL/Mongo execute endpoints; only structured Action/field-edit payloads accepted; Agent refuses unrecognized effect types. |

#### SR-ACTION-002
| | |
| --- | --- |
| **REQUIREMENT** | Cloud and Agent SHALL enforce server-side parameter validation and `max_affected_records` (or equivalent) limits from the authorized definition/envelope. Client-supplied higher limits SHALL be ignored or rejected. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-ACTION-002 |
| **VERIFICATION** | Execute attempt with elevated max-records in client body still capped to authorized envelope value; Agent aborts if affected count would exceed bound. |

#### SR-ACTION-003
| | |
| --- | --- |
| **REQUIREMENT** | Preview and execute SHALL bind to a pinned ActionDefinition / ApprovedFieldEditConfig version or content hash. If the definition changes between preview and execute, Cloud SHALL invalidate execution (fail closed) rather than silently running the new definition. |
| **OWNER** | Backend |
| **THREAT / SOURCE** | TM-ACTION-003 |
| **VERIFICATION** | Change Action definition after preview → confirm/execute rejected until new preview. |

---

### 2.8 Preview + confirmation (SR-PREVIEW)

#### SR-PREVIEW-001
| | |
| --- | --- |
| **REQUIREMENT** | Preview SHALL NOT mutate customer data. Preview SHALL use live customer DB state when safely possible, and SHALL surface honest limitation flags when preview is non-authoritative, partial, or subject to drift. Automated tests SHALL prove preview leaves customer data unchanged for supported engines. |
| **OWNER** | Agent + Backend + QA |
| **THREAT / SOURCE** | TM-PREVIEW-001; SEC-DM-001 |
| **VERIFICATION** | DB checksum/row-version before/after preview unchanged; limitation flags present in API when applicable. |

#### SR-PREVIEW-002
| | |
| --- | --- |
| **REQUIREMENT** | Confirmation SHALL bind to the intended `operation_id` and preview fingerprint (including Action/config pin and authorized effect digest). Confirmation tokens/records SHALL be single-use. Cloud SHALL NOT mint mutation authorization or dispatch a mutating command before a valid Confirmation for that Operation. |
| **OWNER** | Backend |
| **THREAT / SOURCE** | TM-PREVIEW-002; TM-PREVIEW-003 |
| **VERIFICATION** | Confirm for operation A cannot authorize operation B; second use of confirm token fails; mutate outbox empty until confirm succeeds. |

---

### 2.9 Execution / idempotency (SR-EXEC)

#### SR-EXEC-001
| | |
| --- | --- |
| **REQUIREMENT** | Operation results SHALL be accepted only from authenticated Agent result paths per Issue #3. Timeouts after dispatch SHALL NOT automatically mean failure or success. Uncertain outcomes SHALL remain `unknown` until reconciliation establishes the outcome. Cloud SHALL NOT fabricate success. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-EXEC-001 |
| **VERIFICATION** | Drop result / kill Agent mid-flight → Operation stays pending/`unknown`, not succeeded; unauthenticated result POST rejected. |

---

### 2.10 Audit (SR-AUDIT)

#### SR-AUDIT-001
| | |
| --- | --- |
| **REQUIREMENT** | Cloud SHALL append audit events according to the **V1 Audit Event Catalog** below. Events SHALL be append-only records (see SR-AUDIT-002). Secret values SHALL be redacted per SR-REDACT-001. Unrestricted full-row snapshots and raw secrets SHALL NOT be stored. |
| **OWNER** | Backend |
| **THREAT / SOURCE** | TM-AUDIT-001 |
| **VERIFICATION** | For each catalog row marked REQUIRED: an automated test triggers the WHEN condition and asserts (a) exactly one matching event type is recorded, (b) every REQUIRED FIELD is present and non-empty (except fields marked N/A for that type), (c) BEFORE/AFTER matches the catalog rule, (d) no secret substrings from SR-REDACT-001 appear in the event body. Golden path MUST cover at least: `mutation_succeeded`, `mutation_rejected` (authz deny), `preview_completed`, `confirmation_completed`, `rollback_succeeded`, `agent_enrolled`, `agent_revoked`. |

##### V1 Audit Event Catalog (normative for SR-AUDIT-001)

Common fields (include when applicable; omit only when N/A for the event):

| Field | Notes |
| --- | --- |
| `event_type` | Exact catalog name |
| `timestamp` | Server event time (UTC) |
| `actor_id` | Authenticated actor; system/Agent identity when no user |
| `organization_id` | Tenant org |
| `environment_id` | When environment-scoped |
| `operation_id` | When an Operation exists |
| `action_or_config_id` | ActionDefinition / ApprovedFieldEditConfig identity + pin/version when known |
| `target_summary` | Non-secret identifier summary (table/collection + key fields as allowed) |
| `status` / `result` | Outcome enum for the event |
| `rollback_of_operation_id` | Required on rollback events; links to original Operation |

**Before/after principle:** store only redacted, representable mutated field values. Do **not** store secrets or unrestricted full-row dumps.

| Event type | WHEN REQUIRED | REQUIRED FIELDS (in addition to `event_type`, `timestamp`) | BEFORE/AFTER |
| --- | --- | --- | --- |
| `mutation_requested` | Cloud accepts a mutate intent / begins mutate authorization path for an Operation | `actor_id`, `organization_id`, `environment_id`, `operation_id`, `action_or_config_id`, `target_summary`, `status` | **N/A** |
| `mutation_rejected` | Mutate path denied (authz, env mismatch, confirmation missing/invalid, definition pin mismatch, envelope invalid, or other fail-closed reject before customer-data mutation) | `actor_id`, `organization_id`, `environment_id` (if known), `operation_id` (if known), `action_or_config_id` (if known), `target_summary` (if known), `status` (include reject reason class, not secrets) | **N/A** |
| `preview_completed` | Preview completes (success or failed preview attempt that was authorized to run) for an Operation that can gate mutation | `actor_id`, `organization_id`, `environment_id`, `operation_id`, `action_or_config_id`, `target_summary`, `status`; MAY reference a preview fingerprint / limitation flags | **N/A** for mutation before/after; MAY include non-mutating preview summary reference only |
| `confirmation_completed` | Confirmation record successfully created for an Operation | `actor_id`, `organization_id`, `environment_id`, `operation_id`, `action_or_config_id`, `status` | **N/A** |
| `mutation_succeeded` | Agent/Cloud records successful customer-data mutation for an Operation | `actor_id`, `organization_id`, `environment_id`, `operation_id`, `action_or_config_id`, `target_summary`, `status` | **REQUIRED** for mutated fields that are representable after redaction; if a mutated field cannot be represented without violating SR-REDACT-001, record field name + `redacted` marker and omit raw value |
| `mutation_failed` | Mutation attempted and definitively failed without applying the intended customer-data change (or applied none) | same as `mutation_succeeded` field set | **OPTIONAL**; include before image if captured pre-attempt; after SHOULD reflect unchanged or partial-apply state if representable |
| `mutation_unknown` | Outcome is indeterminate after dispatch (timeout/crash/reconnect); Operation remains `unknown` pending reconciliation | `actor_id`, `organization_id`, `environment_id`, `operation_id`, `action_or_config_id`, `target_summary`, `status=unknown` | **N/A** until reconciliation; a later terminal event (`mutation_succeeded` / `mutation_failed`) SHALL be appended when outcome is established |
| `rollback_requested` | Rollback Operation is requested / authorized to start | `actor_id`, `organization_id`, `environment_id`, `operation_id` (rollback op), `rollback_of_operation_id`, `action_or_config_id` (if applicable), `target_summary`, `status` | **N/A** |
| `rollback_succeeded` | Rollback mutation succeeds | same as `rollback_requested` + terminal `status` | **REQUIRED** for reverted fields that are representable after redaction (same redaction rule as `mutation_succeeded`) |
| `rollback_failed` | Rollback attempted and definitively fails, or is rejected because safe rollback cannot be proven | same as `rollback_requested` + terminal `status` / reject reason class | **OPTIONAL**; include before image if captured |
| `agent_enrolled` | Agent enrollment completes successfully | `actor_id` (enrolling admin), `organization_id`, `environment_id`, Agent id in `target_summary` or dedicated `agent_id` field, `status` | **N/A** |
| `agent_revoked` | Agent revocation completes | `actor_id`, `organization_id`, `environment_id`, Agent id, `status` | **N/A** |

Events in this catalog are the **minimum** V1 set. Implementations MAY emit additional event types but SHALL NOT omit REQUIRED emissions above.

#### SR-AUDIT-002
| | |
| --- | --- |
| **REQUIREMENT** | Audit storage SHALL be append-oriented. Ordinary application and operations users SHALL NOT be able to modify or delete audit history via product APIs. Retention SHALL be at least **one year**. Enterprise WORM hardware is NOT required for V1. |
| **OWNER** | Backend + Platform |
| **THREAT / SOURCE** | TM-AUDIT-001; Founder audit retention lock |
| **VERIFICATION** | App DB role lacks UPDATE/DELETE on audit tables (or equivalent trigger deny); API update/delete audit endpoints absent/denied; retention config ≥ 365 days. |

---

### 2.11 Redaction / sensitive data (SR-REDACT)

#### SR-REDACT-001
| | |
| --- | --- |
| **REQUIREMENT** | The following secret classes MUST NOT appear in Cloud database records (except non-secret references), audit event bodies, normal application logs, API error responses, browser responses, or telemetry: customer DB credentials/connection secrets, enrollment tokens (raw), Agent private keys, Cloud signing private keys, and session/authorization bearer secrets. `customer_secret_ref` MAY be stored as an opaque reference/label only. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-DB-001; TM-AUDIT-002; Issue #4 SR-REDACT / SR-SECRETS |
| **VERIFICATION** | Schema review: no password columns on Connection; log/audit fixture tests assert redaction; Agent result payloads strip DSN/password fields. |

---

### 2.12 Rollback (SR-ROLLBACK)

#### SR-ROLLBACK-001
| | |
| --- | --- |
| **REQUIREMENT** | Rollback SHALL be offered only for eligible operations. Cloud SHALL verify required preconditions (including that relevant target state has not changed unsafely where the operation’s safety semantics require it). Rollback SHALL use separate authorization, confirmation where required, a **new** Operation linked to the original, correct org/environment/connection binding, a new signed envelope, and a separate audit trail. If safe rollback cannot be proven, Cloud/Agent SHALL fail closed. Rollback SHALL NOT be unconditional snapshot restore. |
| **OWNER** | Backend + Agent |
| **THREAT / SOURCE** | TM-ROLLBACK-001 |
| **VERIFICATION** | Ineligible rollback rejected; cross-tenant/env rollback denied; successful rollback creates child Operation + audit link; state-changed target refuses rollback. |

---

### 2.13 Secrets (SR-SECRETS)

#### SR-SECRETS-001
| | |
| --- | --- |
| **REQUIREMENT** | TinyAdmin Cloud SHALL NEVER store customer database credentials. Credentials SHALL remain in customer infrastructure and be usable only by the TinyAdmin Agent / local secret mechanism referenced by `customer_secret_ref`. Customer databases SHALL NOT require public `5432`/`27017` (or equivalent Mongo) exposure for TinyAdmin; connectivity remains outbound-Agent / customer-side (established architecture). |
| **OWNER** | Backend + Agent + Platform |
| **THREAT / SOURCE** | Established property #1–#3; TM-DB-001 |
| **VERIFICATION** | **Product / automated (Cloud):** (1) Cloud API and schema reject or refuse persistence of raw DB username/password/DSN fields; only non-secret `customer_secret_ref` (or equivalent opaque reference) is accepted and persisted. (2) Fixture/tests assert no credential columns on Connection and no raw credentials in Cloud DB. **Architecture / Platform / Agent evidence (not sole Cloud app tests):** (3) Documented connectivity path shows Agent initiates outbound encrypted connection and customer DB need not expose public `5432`/`27017` to TinyAdmin Cloud; evidence = architecture conformance note + Agent connectivity test or runbook demonstration. Outbound-only architecture is not weakened. |

---

### 2.14 FA-CONN-BLAST compensating controls (SR-CONN)

#### SR-CONN-001
| | |
| --- | --- |
| **REQUIREMENT** | V1 MAY allow one Agent to manage multiple Connections within its bound organization and environment. One-Agent-per-database is **NOT** mandated. FA-CONN-BLAST residual **HIGH** (Agent-host compromise can reach all Connections on that Agent) remains acknowledged. Compensating controls **CC-1..CC-7** SHALL be realized as specified in the verification matrix below (product-enforced vs customer/ops). |
| **OWNER** | Agent + Backend + Platform |
| **THREAT / SOURCE** | FA-CONN-BLAST; CC-1..CC-7 |
| **VERIFICATION** | Every CC row below MUST have objective evidence before Connection multi-binding ships. Product rows require automated CI/integration/API/Agent tests. Ops/Docs rows require the named checklist artifact present and reviewed (no enterprise certification process). |

##### CC-1..CC-7 verification matrix (normative for SR-CONN-001)

| CC | Control | Enforcement | Objective verification method | Expected evidence |
| --- | --- | --- | --- | --- |
| **CC-1** | Dedicated least-privilege customer DB user per Connection | **Ops/Docs** (customer responsibility; product MAY document only) | Manual checklist: Connection runbook states dedicated DB user + least-privilege grant guidance; sample grant snippet or link present | `docs/` Connection hardening guidance merged; checklist item `CC-1-least-privilege-user` signed off on Connection feature PR |
| **CC-2** | Explicit Connection configuration only (no implicit/undeclared Connection use) | **Product** | Integration test: mutation/preview targeting a Connection id that is not an explicitly configured Cloud Connection for that org/env is denied; Agent has no path to open an ad-hoc Connection from browser-supplied endpoints | Failing-closed API/Agent tests in CI |
| **CC-3** | Per-Connection local secret isolation where practical | **Ops/Docs** (+ Product accepts only opaque refs) | Manual checklist: guidance recommends distinct secret refs / files / OS secret entries per Connection when practical; Product test: Cloud stores only opaque `customer_secret_ref`, never raw secrets | Hardening doc checklist item `CC-3-secret-isolation`; Cloud schema/API tests for refs-only |
| **CC-4** | Agent Connection allowlist matching Cloud Connection IDs | **Product** | Agent integration test: command or local config referencing Connection id **outside** allowlist is rejected; allowlist update required before success | CI Agent test log + assertion |
| **CC-5** | Agent immutable org/environment binding | **Product** | API/DB test: post-activation rebind of Agent org/env rejected; command with mismatched org/env rejected by Cloud and Agent | CI tests (aligns with SR-AGENT-003 / SR-ENV-001) |
| **CC-6** | Command→Connection binding in signed envelopes; mismatch reject | **Product** | Tamper test: valid envelope rewritten with different `connection_id` fails Agent verify; Cloud mint binds server-resolved Connection only | CI Cloud+Agent tests (aligns with SR-CMD-001) |
| **CC-7** | Guidance for customers needing stronger isolation (separate Agent / host) | **Ops/Docs** | Manual checklist: docs explicitly advise separate Agent (and preferably separate host) when reduced blast radius is required; states one-Agent-many-Connections remains supported default | Docs section present; checklist item `CC-7-stronger-isolation-guidance` signed off |

**Topology regression check (Product):** automated or review assertion that the product does **not** require exactly one Connection per Agent to function.

---

## 3. Requirement count summary

| Area | IDs | Count |
| --- | --- | --- |
| SR-TENANT | 001–002 | 2 |
| SR-AUTHZ | 001–003 | 3 |
| SR-ENV | 001–002 | 2 |
| SR-AGENT | 001–003 | 3 |
| SR-CMD | 001–002 | 2 |
| SR-DISCOVERY | 001 | 1 |
| SR-ACTION | 001–003 | 3 |
| SR-PREVIEW | 001–002 | 2 |
| SR-EXEC | 001 | 1 |
| SR-AUDIT | 001–002 | 2 |
| SR-REDACT | 001 | 1 |
| SR-ROLLBACK | 001 | 1 |
| SR-SECRETS | 001 | 1 |
| SR-CONN | 001 | 1 |
| **Total** | | **25** |

---

## 4. V1 implementation blockers (feature-scoped)

These requirements MUST exist before the named feature can safely ship. Unrelated features MAY proceed independently.

| Feature | Blocked until |
| --- | --- |
| Agent enrollment / session | SR-AGENT-001..003, SR-SECRETS-001, SR-REDACT-001 (token/key paths) |
| Connection configuration | SR-TENANT-*, SR-ENV-001, SR-SECRETS-001, SR-CONN-001 (allowlist + refs) |
| Discovery / search | SR-DISCOVERY-001, SR-TENANT-*, SR-REDACT-001 |
| Safe Action / field-edit **definition** | SR-AUTHZ-001, SR-TENANT-*, SR-ACTION-001 |
| Preview | SR-PREVIEW-001, SR-AUTHZ-*, SR-ENV-*, SR-ACTION-003 (pin), SR-DISCOVERY authz where used |
| Mutation confirmation / dispatch / execute | SR-AUTHZ-*, SR-ENV-*, SR-CMD-*, SR-ACTION-*, SR-PREVIEW-002, SR-EXEC-001, SR-AUDIT-001, SR-REDACT-001, SR-CONN-001 (binding) |
| Rollback | SR-ROLLBACK-001, SR-AUTHZ-003, SR-CMD-*, SR-AUDIT-*, SR-ENV-* |
| Production Go-live (org-wide) | All CRITICAL/HIGH Issue #4 mitigations mapped above implemented or Founder-accepted; audit retention SR-AUDIT-002 |

---

## 5. Implementation security gates (PR mapping)

| Change type | Security review |
| --- | --- |
| Authentication / session / invite / password-reset | **Required** |
| Authorization / RBAC / membership | **Required** |
| Tenant isolation / org scoping / multi-org context | **Required** |
| Environment isolation / production confirmation UX that affects gates | **Required** |
| Agent enrollment, protocol, command verification, revoke/cancel | **Required** |
| Mutation execution / Safe Actions / approved-field edits / preview | **Required** |
| Audit / redaction / rollback | **Required** |
| Connection allowlist / secret-ref handling / FA-CONN-BLAST controls | **Required** |
| Normal styling / copy / layout without authz, confirmation, or boundary changes | **Not** mandatory |
| Routine non-security frontend work | **Not** mandatory unless it changes authorization, confirmation, or security boundaries |

Do not create additional process bureaucracy beyond this table.

---

## 6. CR-TM-001 disposition

Independent Code Review on PR #15 recorded **CR-TM-001** (also referenced as CR-TM-001) (LOW, non-blocking): discovery/search lacked an explicit QA hook in the threat register.

**Disposition for Issue #5:** Addressed by **SR-DISCOVERY-001** without reopening Issue #4 or adding a new threat row. No Founder decision required.

---

## 7. FA-CONN-BLAST

Preserves approved V1 topology: one Agent may manage multiple Connections.

Compensating controls CC-1..CC-7 are formalized as **SR-CONN-001**.

**Residual risk (HIGH):** Agent-host compromise can yield direct credential/data-plane access to all Connections on that Agent, bypassing Cloud Safe Action policy for direct DB access.

**Founder decision:** **Not required** for Issue #5. No new evidence that CC-1..CC-7 are inadequate.

---

## 8. Founder decisions required

**None.** Issue #5 does not reopen Issues #1/#2/#3/#4/#6 product or architecture locks.

---

## 9. Consistency with merged gates

| Gate | Check |
| --- | --- |
| #2 architecture | Preserves outbound Agent, no Cloud DB creds, tenant/env isolation, Safe Action model |
| #3 protocol | Defers crypto/canonicalization to ADR 0007; adds only implementation SHALLs |
| #4 threat model | Each SR traces to TM-* / FA-CONN-BLAST / CR-TM-001 |
| #6 domain | Aligns with define≠run, confirm-before-mutate, preview non-mutate, conditional rollback, append-only audit |

**No CRITICAL/HIGH contradiction found that requires architecture redesign.**

---

## 10. Document history

| Date | Change |
| --- | --- |
| 2026-09-23 | Production-MVP Issue #5 security requirements (25 SHALLs) from merged Issue #4 handoff |
| 2026-09-23 | QA testability remediation (PR #16): SR-CONN-001 CC matrix; SR-AUDIT-001 V1 event catalog; SR-AUTHZ-002 revoke determinism; SR-SECRETS-001 split product vs architecture evidence; SR-ENV-002 Issue #7 manual evidence pointer |
